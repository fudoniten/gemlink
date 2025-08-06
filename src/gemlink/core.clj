(ns gemlink.core
  (:require
   [clojure.core.async :refer [<! chan go]]
   [clojure.stacktrace :refer [print-stack-trace]]
   [clojure.string :as str]

   [gemlink.middleware :refer [base-middleware]]
   [gemlink.logging :as log]
   [gemlink.utils :refer [cond-let parse-route-config]]
   [gemlink.path :refer [build-path]]
   [gemlink.response :refer [not-found-error]])

  (:import

   (java.io
    FileInputStream)

   (java.net
    Socket
    SocketException)

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
  (let [running? (atom true)
        logged-base (base-middleware :logger logger)
        full-handler (logged-base handler)]
    (log/info! logger "listening on server socket for incoming requests...")
    (doto (Thread.
           (fn []
             (log/debug! logger "request thread listening...")
             (while @running?
               (try
                 (let [^Socket client (.accept server-sock)]
                   (log/debug! logger "handling request...")
                   (future
                     (try (full-handler client)
                          (catch Exception e
                            (println (format "unexpected exception serving request: %s"
                                             (.getMessage e)))
                            (println (with-out-str (print-stack-trace e)))))))
                 (catch SocketException _
                   (log/info! logger "socket closed, shutting down listener...")
                   (reset! running? false))
                 (catch Exception e
                   (log/error! logger (format "error handling request: %s\n%s"
                                              (.getMessage e)
                                              (with-out-str (print-stack-trace e)))))))))
      (.start))))

(defn fold-middleware
  "Take a list of middleware functions (-> handler (-> req resp)) and return a middleware function."
  [& middlewares]
  (reduce (fn [acc mw]
            (fn [handler]
              (acc (mw handler))))
          identity
          middlewares))

(defn route-matcher
  [{:keys [children handler middleware]
    :or   {middleware []}}]
  (let [mw-fn (apply fold-middleware (reverse middleware))
        path-handlers (into {}
                            (for [[path path-cfg] children]
                              [path (route-matcher path-cfg)]))]
    (fn [{:keys [remaining-path] :as req}]
      (let [[next & rest] remaining-path]
        (cond-let [base-handler handler]
                  (let [wrapped-handler (mw-fn base-handler)]
                    (wrapped-handler (assoc req :remaining-path (build-path remaining-path))))

                  [path-handler (get path-handlers next)]
                  (let [wrapped-handler (mw-fn path-handler)]
                    (wrapped-handler (assoc req :remaining-path rest)))

                  [[param param-handler] (first (filter (fn [[k _]] (str/starts-with? k ":"))
                                                        path-handlers))]
                  (let [wrapped-handler (mw-fn param-handler)
                        param-key (keyword (subs param 1))]
                    (wrapped-handler (-> req
                                         (assoc :remaining-path rest)
                                         (update :params assoc param-key next))))

                  :else (not-found-error (format "path not found")))))))

(defn define-routes
  [{:keys [middleware handler]} routes]
  (-> {:middleware middleware
       :handler    handler
       :children   (parse-route-config routes)}
      (route-matcher)))

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
