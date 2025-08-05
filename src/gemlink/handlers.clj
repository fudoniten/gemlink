(ns gemlink.handlers
  (:require [clojure.stacktrace :refer [print-stack-trace]]

            [gemlink.logging :as log]
            [gemlink.utils :refer [generate-listing generate-listing mime-type pretty-format]]
            [gemlink.response :refer [success not-found-error bad-request-error unknown-server-error response? get-status get-body get-header]]
            [gemlink.path :refer [get-file-contents join-paths split-path build-path] :as path])
  (:import clojure.lang.ExceptionInfo
           (java.io
            BufferedReader
            InputStreamReader
            OutputStreamWriter)))

(defn static-handler
  [body]
  (fn [_] (success body)))

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
              response (handler request)]
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

(defn path-handler
  [path {:keys [listing? index-file mime-type-reader]
         :or   {listing?          false}}]
  (fn [{:keys [uri remaining-path]}]
    (if-not remaining-path
      (cond listing?   (success (generate-listing uri path)
                                :mime-type "text/gemini")
            index-file (let [index-file (join-paths path index-file)]
                         (success (get-file-contents index-file)
                                  :mime-type (mime-type index-file)))
            :else      (not-found-error))
      (try (let [full-filename (join-paths path remaining-path)
                 mime-type     (mime-type full-filename mime-type-reader)
                 file-contents (get-file-contents full-filename)]
             (success file-contents :mime-type mime-type))
           (catch ExceptionInfo e
             (case (:type (ex-data e))
               ::path/unauthorized-access
               (bad-request-error "unauthorized")

               ::path/file-not-found
               (not-found-error)

               (unknown-server-error)))))))

(defn users-handler
  [users-path-mapper]
  (fn [{:keys [remaining-path] :as req}]
    (let [[user & remaining] (split-path remaining-path)]
      (if-let [user-handler (users-path-mapper user)]
        (user-handler (assoc req :remaining-path
                             (apply build-path remaining)))
        (not-found-error (format "user not found: %s" user))))))
