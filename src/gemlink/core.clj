(ns gemlink.core
  (:require
   [clojure.core.async :refer [<! chan go]]
   [clojure.stacktrace :refer [print-stack-trace]]
   [clojure.string :as str]
   [clojure.pprint :refer [pprint]]

   [gemlink.middleware :refer [base-middleware]]
   [gemlink.logging :as log]
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
  [{:keys [children handler middleware logger]
    :or   {middleware []}}]
  (let [mw-fn (apply fold-middleware middleware)
        pprint-str (fn [o] (with-out-str (pprint o)))
        path-handlers (into {} (map (fn [[path path-cfg]]
                                      (log/debug! logger (format "adding route for child %s: %s"
                                                                 path (pprint-str path-cfg)))
                                      [path (route-matcher path-cfg)])
                                    children))]
    (mw-fn
     (fn [{:keys [remaining-path] :as req}]
       (log/debug! logger (format "routing request: %s"
                                  (pretty-format req)))
       (let [[next & rest] remaining-path]
         (cond-let [path-handler (get path-handlers next)]
                   (do (log/debug! logger (format "matched path handler: %s" next))
                       (path-handler (assoc req :remaining-path rest)))

                   [[param param-handler] (first (filter (fn [[k _]] (str/starts-with? k ":"))
                                                         path-handlers))]
                   (let [param-key (keyword (subs param 1))]
                     (log/debug! logger (format "matched parameter: %s"
                                                param))
                     (param-handler (-> req
                                        (assoc :remaining-path rest)
                                        (update :params assoc param-key next))))

                   [base-handler handler]
                   (do (log/debug! logger (format "matched base handler"))
                       (base-handler (assoc req :remaining-path (build-path remaining-path))))

                   :else (not-found-error (format "path not found"))))))))

(defn define-routes
  [opts routes]
  (-> (assoc opts :children (parse-route-config routes))
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
