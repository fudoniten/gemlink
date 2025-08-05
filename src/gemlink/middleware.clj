(ns gemlink.middleware
  (:require [clojure.stacktrace :refer [print-stack-trace]]
            [clojure.string :as str]

            [gemlink.logging :as log]
            [gemlink.utils :refer [pretty-format]]
            [gemlink.response :refer [bad-request-error unknown-server-error response? get-status get-header get-body]])
  (:import
   (java.net
    URI
    URISyntaxException)))

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
