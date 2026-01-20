(ns gemlink.core
  (:require
   [clojure.core.async :refer [<! chan go]]
   [clojure.stacktrace :refer [print-stack-trace]]
   [clojure.string :as str]
   [clojure.pprint :refer [pprint]]
   [taoensso.timbre :as log]

   [gemlink.middleware :refer [base-middleware]]
   [gemlink.utils :refer [cond-let parse-route-config pretty-format]]
   [gemlink.path :refer [build-path]]
   [gemlink.response :refer [not-found-error]])

  (:import

   (java.io
    FileInputStream)

   (java.net
    Socket
    SocketException)

   (java.security KeyStore)

   (java.util.concurrent
    Executors
    ExecutorService
    TimeUnit)

   (javax.net.ssl KeyManagerFactory SSLContext)))


(defn load-ssl-context
  "Loads an SSL context from a keystore file using the provided password.
   
   Options:
     :keystore-type - Keystore format (default: \"PKCS12\"). Also supports \"JKS\", etc.
     :tls-protocol - TLS protocol version (default: \"TLS\"). Options: \"TLSv1.2\", \"TLSv1.3\", etc.
     :key-manager-algorithm - Algorithm for KeyManagerFactory (default: \"SunX509\")
   
   Examples:
     ;; PKCS12 keystore (default)
     (load-ssl-context \"keystore.p12\" \"password\")
     
     ;; JKS keystore with TLSv1.3
     (load-ssl-context \"keystore.jks\" \"password\" :keystore-type \"JKS\" :tls-protocol \"TLSv1.3\")"
  [^String keystore-path ^String password & {:keys [keystore-type tls-protocol key-manager-algorithm]
                                              :or {keystore-type "PKCS12"
                                                   tls-protocol "TLS"
                                                   key-manager-algorithm "SunX509"}}]
  (let [ks (KeyStore/getInstance keystore-type)]
    (with-open [ks-stream (FileInputStream. keystore-path)]
      (.load ks ks-stream (.toCharArray password)))
    (let [factory (KeyManagerFactory/getInstance key-manager-algorithm)]
      (.init factory ks (.toCharArray password))
      (doto (SSLContext/getInstance tls-protocol)
        (.init (.getKeyManagers factory) nil nil)))))


(defn serve-requests
  "Listens for incoming requests on the server socket and handles them using the provided handler.
   Uses a bounded thread pool to prevent resource exhaustion under load.
   
   Options:
     :max-concurrent-requests - Maximum concurrent requests (default: 50)
     :shutdown-timeout-seconds - Time to wait for in-flight requests on shutdown (default: 30)"
  [{:keys [max-concurrent-requests shutdown-timeout-seconds]
    :or   {max-concurrent-requests 50
           shutdown-timeout-seconds 30}} ^Socket server-sock handler]
  (let [running?      (atom true)
        active-count  (atom 0)
        ^ExecutorService executor (Executors/newFixedThreadPool max-concurrent-requests)
        logged-base   (base-middleware)
        full-handler  (logged-base handler)]
    (log/info (format "listening on server socket for incoming requests (max %d concurrent)..."
                      max-concurrent-requests))
    {:thread  (doto (Thread.
                     (fn []
                       (log/debug "request thread listening...")
                       (while @running?
                         (try
                           (let [^Socket client (.accept server-sock)]
                             (log/debug "handling request...")
                             (.submit executor
                                      ^Runnable
                                      (fn []
                                        (swap! active-count inc)
                                        (try
                                          (full-handler client)
                                          (catch Exception e
                                            (log/error (format "unexpected exception serving request: %s"
                                                               (.getMessage e)))
                                            (log/debug (with-out-str (print-stack-trace e))))
                                          (finally
                                            (swap! active-count dec))))))
                           (catch SocketException _
                             (log/info "socket closed, shutting down listener...")
                             (reset! running? false))
                           (catch Exception e
                             (log/error (format "error handling request: %s\n%s"
                                                (.getMessage e)
                                                (with-out-str (print-stack-trace e)))))))
                       ;; Shutdown executor when loop exits
                       (log/info (format "shutting down request executor... (%d active requests)" @active-count))
                       (.shutdown executor)
                       (if (.awaitTermination executor shutdown-timeout-seconds TimeUnit/SECONDS)
                         (log/info "graceful shutdown completed")
                         (do
                           (log/warn (format "shutdown timeout exceeded, forcing shutdown (%d requests still active)" @active-count))
                           (.shutdownNow executor)))))
                (.start))
     :executor executor
     :active-count active-count}))

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
  (let [mw-fn (apply fold-middleware middleware)
        pprint-str (fn [o] (with-out-str (pprint o)))
        path-handlers (into {} (map (fn [[path path-cfg]]
                                      (log/debug (format "adding route for child %s: %s"
                                                         path (pprint-str path-cfg)))
                                      [path (route-matcher path-cfg)])
                                    children))]
    (mw-fn
     (fn [{:keys [remaining-path] :as req}]
       (log/debug (format "routing request: %s"
                          (pretty-format req)))
       (let [[next & rest] remaining-path]
         (cond-let [path-handler (get path-handlers next)]
                   (do (log/debug (format "matched path handler: %s" next))
                       (path-handler (assoc req :remaining-path rest)))

                   [[param param-handler] (first (filter (fn [[k _]] (str/starts-with? k ":"))
                                                         path-handlers))]
                   (let [param-key (keyword (subs param 1))]
                     (log/debug (format "matched parameter: %s"
                                        param))
                     (param-handler (-> req
                                        (assoc :remaining-path rest)
                                        (update :params assoc param-key next))))

                   [base-handler handler]
                   (do (log/debug (format "matched base handler"))
                       (base-handler (assoc req :remaining-path (build-path remaining-path))))

                   :else (not-found-error (format "path not found"))))))))

(defn define-routes
  [opts routes]
  (-> (assoc opts :children (parse-route-config routes))
      (route-matcher)))

(defn start-server
  "Starts a Gemini server on the specified port using the provided SSL context and handler.
   Returns a map with:
     :stop-chan - Channel to trigger shutdown (send any value to stop)
     :server-info - Server information including active connection count
   
   To stop the server gracefully, put a value on the :stop-chan:
     (async/put! (:stop-chan server) :stop)"
  [{:keys [ssl-context port] :as ctx} handler]
  (let [server-sock (.createServerSocket (.getServerSocketFactory ssl-context) port)
        stop-chan (chan)
        server-info (serve-requests ctx server-sock handler)]
    (log/info (format "gemlink server listening on port %s" port))
    (go (<! stop-chan)
        (log/info (format "shutting down gemlink listener on port %s" port))
        (try
          (.close server-sock)
          (catch Exception e
            (log/error (format "failed to close gemlink server socket: %s" (.getMessage e))))))
    {:stop-chan stop-chan
     :server-info server-info}))
