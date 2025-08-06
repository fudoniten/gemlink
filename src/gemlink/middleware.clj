(ns gemlink.middleware
  (:require [clojure.stacktrace :refer [print-stack-trace]]
            [clojure.string :as str]

            [gemlink.logging :as log]
            [gemlink.utils :refer [pretty-format]]
            [gemlink.response :refer [bad-request-error unknown-server-error response? get-status get-header get-body]])
  (:import
   (java.net
    URI
    URISyntaxException)
   (java.io
    BufferedReader
    InputStreamReader
    OutputStreamWriter)))

(defn base-middleware
  "Basic Gemini middleware, taking a socket, reading the request, and calling the
  supplied handler."
  [& {:keys [logger]}]
  (fn [handler]
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
            (.close client)))))))

(defn parse-url
  "Parses the request line into a URI and adds it to the request."
  [handler]
  (fn [{:keys [request-line] :as req}]
    (try
      (handler (assoc req :uri (URI. (str/trim request-line))))
      (catch URISyntaxException _
        (bad-request-error (format "invalid url: %s" request-line)))
      (catch Exception e))))

(defn extract-path
  "Extract the URI path from :uri for routing."
  [& {:keys [logger]}]
  (log/debug! logger "extracting path")
  (fn [handler]
    (fn [{:keys [uri] :as req}]
      (if-not uri
        (do (log/error! logger "request missing uri, can't extract path, aborting!")
            (unknown-server-error "server misconfigured"))
        (handler (assoc req
                        :remaining-path (rest (str/split (.getPath uri) #"/"))
                        :full-path      (.getPath uri)))))))

(defn log-requests
  "Logs incoming requests using the provided logger, at debug level."
  [& {:keys [logger]}]
  (fn [handler]
    (fn [req]
      (log/debug! logger "#####\n# REQUEST\n#####")
      (log/debug! logger (pretty-format req))
      (handler req))))

(defn log-responses
  "Logs outgoing responses using the provided logger, at debug level."
  [& {:keys [logger]}]
  (fn [handler]
    (fn [req]
      (let [resp (handler req)]
        (log/debug! logger "#####\n# RESPONSE\n#####")
        (log/debug! logger resp)
        resp))))

(defn ensure-return
  "Catch all exceptions, log them, and ensure something is returned to the client."
  [& {:keys [logger]}]
  (fn [handler]
    (fn [req]
      (try
        (handler req)
        (catch Exception e
          (log/error! logger (format "error serving request: %s"
                                     (.getMessage e)))
          (log/debug! logger (with-out-str (print-stack-trace e)))
          (unknown-server-error "unknown server error"))))))
