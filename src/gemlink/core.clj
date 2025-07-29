(ns gemlink.core
  (:require
   [clojure.core.async :refer [<! chan go]]
   [clojure.pprint :refer [pprint]]
   [clojure.stacktrace :refer [print-stack-trace]]
   [clojure.string :as str]
   [gemlink.logging :as log])
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

(defn pretty-format [o]
  (with-out-str (pprint o)))

(defn load-ssl-context
  [^String keystore-path ^String password]
  (let [ks (KeyStore/getInstance "PKCS12")]
    (with-open [ks-stream (FileInputStream. keystore-path)]
      (.load ks ks-stream (.toCharArray password)))
    (let [factory (KeyManagerFactory/getInstance "SunX509")]
      (.init factory ks (.toCharArray password))
      (doto (SSLContext/getInstance "TLS")
        (.init (.getKeyManagers factory) nil nil)))))

(defprotocol Response
  (get-status [_])
  (get-header [_])
  (get-body   [_]))

(defn success
  [^String mime-type ^String body]
  (reify Response
    (get-status [_] 20)
    (get-header [_] mime-type)
    (get-body   [_] body)))

(defn bad-request-error
  [^String message]
  (reify Response
    (get-status [_] 59)
    (get-header [_] "bad request")
    (get-body   [_] message)))

(defn unknown-server-error
  [^String message]
  (reify Response
    (get-status [_] 40)
    (get-header [_] "unknown error")
    (get-body   [_] message)))

(defn response? [o] (satisfies? Response o))

(defn serve-requests
  [{:keys [logger]} ^Socket server-sock handler]
  (let [running?   (atom true)]
    (log/info! logger "listening on server socket for incoming requests...")
    (doto (Thread.
           (fn []
             (log/info! logger "request thread listening...")
             (while @running?
               (try
                 (let [^Socket client (.accept server-sock)]
                   (log/info! logger "handling request...")
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
  [handler {:keys [logger]}]
  (fn [client]
    (log/info! logger "opening streams...")
    (let [in  (-> client (.getInputStream) (InputStreamReader.) (BufferedReader.))
          out (-> client (.getOutputStream) (OutputStreamWriter.))]
      (log/info! logger "streams open!")
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
  [handler]
  (fn [{:keys [request-line] :as req}]
    (try
      (handler (assoc req :uri (URI. (str/trim request-line))))
      (catch URISyntaxException _
        (bad-request-error (format "invalid url: %s" request-line))))))

(defn log-requests
  [handler {:keys [logger]}]
  (fn [req]
    (log/debug! logger "#####\n# REQUEST\n#####")
    (log/debug! logger (pretty-format req))
    (handler req)))

(defn log-responses
  [handler {:keys [logger]}]
  (fn [req]
    (let [resp (handler req)]
      (log/debug! logger "#####\n# RESPONSE\n#####")
      (log/debug! logger resp)
      resp)))

(defn fold-middleware
  "Take a list of middleware functions (-> handler (-> req resp)) and return a middleware function."
  ([] (fn [handler] handler))
  ([middleware] middleware)
  ([middleware & rest] (middleware (fold-middleware rest))))

(defn route-matcher
  [route]
  (fn [{remaining-path :remaining-path :as req}]
    (when (nil? remaining-path)
      (ex-info "failed to match path: :remaining-path unset" {:route route}))
    (if (str/starts-with? remaining-path route)
      (assoc req :remaining-path (str/replace-first remaining-path route ""))
      nil)))

(defn apply-match
  [pred-map o]
  (let [handler (some (fn [[pred handler]] (when (pred o) handler))
                      pred-map)]
    (handler o)))

(defn create-handler
  [{:keys [handler middleware]} subroutes]
  (let [mw-fn (fold-middleware (reverse middleware))
        route-map (map (fn [route & cfg]
                         [(route-matcher route)
                          (if (not (map? (first cfg)))
                            (create-handler {} cfg)
                            (apply create-handler cfg))])
                       subroutes)]
    (if handler
      (mw-fn handler)
      (fn [req] (apply-match route-map req)))))

(defn start-server
  [{:keys [logger ssl-context port] :as ctx} handler]
  (let [server-sock (.createServerSocket (.getServerSocketFactory ssl-context) port)
        stop-chan (chan)]
    (log/info! logger (format "gemlink listening on port %s" port))
    (serve-requests ctx server-sock handler)
    (go (<! stop-chan)
        (log/info! logger (format "shutting down gemlink listener on port %s" port))
        (try
          (.close server-sock)
          (catch Exception e
            (log/error! logger (format "failed to close server socket: %s" (.getMessage e))))))
    stop-chan))
