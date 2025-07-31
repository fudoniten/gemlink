(ns gemlink.core
  (:require
   [clojure.core.async :refer [<! chan go]]
   [clojure.stacktrace :refer [print-stack-trace]]
   [clojure.string :as str]

   [gemlink.logging :as log]
   [gemlink.utils :refer [cond-let pretty-format]]
   [gemlink.response :refer [Response success bad-request-error unknown-server-error not-found-error response?]])

  (:import

   (java.io
    BufferedReader
    FileInputStream
    InputStreamReader
    OutputStreamWriter)

   (java.net
    Socket
    SocketException
    URI
    URISyntaxException)

   (java.security KeyStore)

   (javax.net.ssl KeyManagerFactory SSLContext)))


(defn load-ssl-context
  "Loads an SSL context from a keystore file using the provided password."
  [^String keystore-path ^String password]
  (let [ks (KeyStore/getInstance "PKCS12")]
    (with-open [ks-stream (FileInputStream. keystore-path)]
      (.load ks ks-stream (.toCharArray password)))
    (let [factory (KeyManagerFactory/getInstance "SunX509")]
      (.init factory ks (.toCharArray password))
      (doto (SSLContext/getInstance "TLS")
        (.init (.getKeyManagers factory) nil nil)))))


(defn serve-requests
  "Listens for incoming requests on the server socket and handles them using the provided handler."
  [{:keys [logger]} ^Socket server-sock handler]
  (let [running?   (atom true)]
    (log/info! logger "listening on server socket for incoming requests...")
    (doto (Thread.
           (fn []
             (log/debug! logger "request thread listening...")
             (while @running?
               (try
                 (let [^Socket client (.accept server-sock)]
                   (log/debug! logger "handling request...")
                   (future (handler client)))
                 (catch SocketException _
                   (log/info! logger "socket closed, shutting down listener...")
                   (reset! running? false))
                 (catch Exception e
                   (log/error! logger (format "error handling request: %s\n%s"
                                              (.getMessage e)
                                              (with-out-str (print-stack-trace e)))))))))
      (.start))))

(defn base-handler
  "Basic Gemini handler, taking a socket, reading the request, and calling the
  supplied handler."
  [handler {:keys [logger]}]
  (fn [client]
    (log/debug! logger "opening streams...")
    (let [in  (-> client (.getInputStream) (InputStreamReader.) (BufferedReader.))
          out (-> client (.getOutputStream) (OutputStreamWriter.))]
      (log/debug! logger "streams open!")
      (try
        (.startHandshake client)
        (let [request-line (.readLine in)
              session      (.getSession client)
              request      {:request-line request-line
                            :remote-addr  (.getInetAddress client)
                            :remote-port  (.getPort client)
                            :local-port   (.getLocalPort client)
                            :tls-protocol (.getProtocol session)
                            :tls-cipher   (.getCipherSuite session)}
              ^Response response (handler request)]
          (log/info! logger (format "request: %s" request-line))
          (if-not (response? response)
            (do (log/error! logger (format "handler response was not a Response: %s"
                                           (pretty-format response)))
                (.write out "40 unknown handler error\r\n"))
            (do (.write out (format "%s %s\r\n"
                                    (str (get-status response))
                                    (get-header response)))
                (when-let [body (get-body response)]
                  (.write out body)))))
        (catch Exception e
          (log/error! logger (format "error processing request: %s\n%s"
                                     (.getMessage e)
                                     (with-out-str (print-stack-trace e))))
          (.write out "40 unknown server error\r\n"))
        (finally
          (.flush out)
          (.shutdownOutput client)
          (Thread/sleep 50)
          (.close client))))))

(defn process-url
  "Parses the request line into a URI and adds it to the request."
  [handler]
  (fn [{:keys [request-line] :as req}]
    (try
      (handler (assoc req :uri (URI. (str/trim request-line))))
      (catch URISyntaxException _
        (bad-request-error (format "invalid url: %s" request-line))))))

(defn extract-path
  "Extract the URI path from :uri for routing."
  [handler {:keys [logger]}]
  (fn [{:keys [uri] :as req}]
    (if-not uri
      (do (log/error! logger "request missing uri, can't extract path, aborting!")
          (unknown-server-error "server misconfigured"))
      (handler (assoc req
                      :remaining-path (rest (str/split (.getPath uri) #"/"))
                      :full-path      (.getPath uri))))))

(defn log-requests
  "Logs incoming requests using the provided logger, at debug level."
  [handler {:keys [logger]}]
  (fn [req]
    (log/debug! logger "#####\n# REQUEST\n#####")
    (log/debug! logger (pretty-format req))
    (handler req)))

(defn log-responses
  "Logs outgoing responses using the provided logger, at debug level."
  [handler {:keys [logger]}]
  (fn [req]
    (let [resp (handler req)]
      (log/debug! logger "#####\n# RESPONSE\n#####")
      (log/debug! logger resp)
      resp)))

(defn ensure-return
  "Catch all exceptions, log them, and ensure something is returned to the client."
  [handler {:keys [logger]}]
  (fn [req]
    (try
      (handler req)
      (catch Exception e
        (log/error! logger (format "error serving request: %e"
                                   (.getMessage e)))
        (log/debug! logger (with-out-str (print-stack-trace e)))
        (unknown-server-error "unknown server error")))))

(defn fold-middleware
  "Take a list of middleware functions (-> handler (-> req resp)) and return a middleware function."
  ([] (fn [handler] handler))
  ([middleware] middleware)
  ([middleware & rest] (middleware (fold-middleware rest))))


(defn route-matcher
  "Given a route configuration, return a function from request -> response."
  [{:keys [children handler middleware]}]
  (let [mw-fn (fold-middleware middleware)]
    (if handler
      (mw-fn handler)
      (let [path-handlers (into {}
                                (for [[path child] children]
                                  [path (route-matcher child)]))]
        (fn [{:keys [uri remaining-path]} :as req]
          (let [[next & rest] remaining-path]
            (cond (contains? path-handlers next)
                  (let [wrapped-handler (mw-fn (get path-handlers next))]
                    (wrapped-handler (assoc req :remaining-path rest))))
            (if-not (contains? path-handlers next)
              (not-found-error (format "missing resource: %s" uri))
              (let [wrapped-handler (mw-fn (get path-handlers next))]
                (wrapped-handler (assoc req :remaining-path rest))))))))))


(defn start-server
  "Starts a Gemini server on the specified port using the provided SSL context and handler."
  [{:keys [logger ssl-context port] :as ctx} handler]
  (let [server-sock (.createServerSocket (.getServerSocketFactory ssl-context) port)
        stop-chan (chan)]
    (log/info! logger (format "gemlink server listening on port %s" port))
    (serve-requests ctx server-sock handler)
    (go (<! stop-chan)
        (log/info! logger (format "shutting down gemlink listener on port %s" port))
        (try
          (.close server-sock)
          (catch Exception e
            (log/error! logger (format "failed to close gemlink server socket: %s" (.getMessage e))))))
    stop-chan))
