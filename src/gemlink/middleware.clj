(ns gemlink.middleware
  (:require [clojure.stacktrace :refer [print-stack-trace]]
            [clojure.string :as str]
            [taoensso.timbre :as log]

            [gemlink.utils :refer [pretty-format]]
            [gemlink.response :refer [bad-request-error unknown-server-error response? get-status get-header get-body]])
  (:import
   (java.net
    URI
    URISyntaxException)
   (java.io
    BufferedReader
    InputStreamReader
    OutputStreamWriter)
   (javax.net.ssl
    SSLPeerUnverifiedException)))

(defn base-middleware
  "Basic Gemini middleware, taking a socket, reading the request, and calling the
  supplied handler.
  
  Options:
    :close-delay-ms - Milliseconds to wait between shutdownOutput and close (default: 50).
                      This delay allows clients to receive final data before socket closure."
  [& {:keys [close-delay-ms] :or {close-delay-ms 50}}]
  (fn [handler]
    (fn [client]
      (log/debug "opening streams...")
      (let [in  (-> client (.getInputStream) (InputStreamReader.) (BufferedReader.))
            out (-> client (.getOutputStream) (OutputStreamWriter.))]
        (log/debug "streams open!")
        (try
          (.startHandshake client)
          (let [request-line   (.readLine in)
                session        (.getSession client)
                client-certs   (try
                                 (vec (.getPeerCertificates session))
                                 (catch SSLPeerUnverifiedException _
                                   nil))
                request        {:request-line   request-line
                                :remote-addr    (.getInetAddress client)
                                :remote-port    (.getPort client)
                                :local-port     (.getLocalPort client)
                                :tls-protocol   (.getProtocol session)
                                :tls-cipher     (.getCipherSuite session)
                                :client-certs   client-certs}
                response (handler request)]
            (log/info (format "request: %s" request-line))
            (if-not (response? response)
              (do (log/error (format "handler response was not a Response: %s"
                                     (pretty-format response)))
                  (.write out "40 unknown handler error\r\n"))
              (do (.write out (format "%s %s\r\n"
                                      (str (get-status response))
                                      (get-header response)))
                  (when-let [body (get-body response)]
                    (.write out body)))))
          (catch Exception e
            (log/error (format "error processing request: %s\n%s"
                               (.getMessage e)
                               (with-out-str (print-stack-trace e))))
            (.write out "40 unknown server error\r\n"))
          (finally
            (.flush out)
            (.shutdownOutput client)
            ;; Brief delay to allow client to receive data before socket closure
            (Thread/sleep close-delay-ms)
            (.close client)))))))

(defn parse-url
  "Parses the request line into a URI and adds it to the request. Validates URL length per Gemini spec (max 1024 bytes)."
  [& _opts]
  (fn [handler]
    (fn [{:keys [request-line] :as req}]
      (try
        ;; Gemini protocol spec limits URLs to 1024 bytes
        (when (> (count request-line) 1024)
          (throw (ex-info "url too long" {:type :url-too-long})))
        (handler (assoc req :uri (URI. (str/trim request-line))))
        (catch clojure.lang.ExceptionInfo e
          (if (= (:type (ex-data e)) :url-too-long)
            (bad-request-error "url exceeds maximum length (1024 bytes)")
            (throw e)))
        (catch URISyntaxException _
          (bad-request-error (format "invalid url: %s" request-line)))
        (catch Exception e
          (log/error (format "error parsing url: %s"
                             (.getMessage e)))
          (unknown-server-error "failed to read url"))))))

(defn extract-path
  "Extract the URI path from :uri for routing."
  [& _opts]
  (fn [handler]
    (fn [{:keys [uri] :as req}]
      (log/debug "extracting path")
      (if-not uri
        (do (log/error "request missing uri, can't extract path, aborting!")
            (unknown-server-error "server misconfigured"))
        (handler (assoc req
                        :remaining-path (rest (str/split (.getPath uri) #"/"))
                        :full-path      (.getPath uri)))))))

(defn log-requests
  "Logs incoming requests at debug level."
  [& _opts]
  (fn [handler]
    (fn [req]
      (log/debug "#####\n# REQUEST\n#####")
      (log/debug (pretty-format req))
      (handler req))))

(defn log-responses
  "Logs outgoing responses at debug level."
  [& _opts]
  (fn [handler]
    (fn [req]
      (let [resp (handler req)]
        (log/debug "#####\n# RESPONSE\n#####")
        (log/debug resp)
        resp))))

(defn ensure-return
  "Catch all exceptions, log them, and ensure something is returned to the client."
  [& _opts]
  (fn [handler]
    (fn [req]
      (try
        (handler req)
        (catch Exception e
          (log/error (format "error serving request: %s"
                             (.getMessage e)))
          (log/debug (with-out-str (print-stack-trace e)))
          (unknown-server-error "unexpected server error"))))))
