(ns gemlink.middleware-test
  (:require [gemlink.middleware :refer :all]
            [gemlink.logging :as log]
            [gemlink.response :refer (get-status get-body success)]
            [clojure.test :refer [deftest is testing run-tests]])
  (:import java.net.URI))

(deftest test-parse-url
  (let [mw-fn (parse-url :logger (log/print-logger :fatal))
        handler (mw-fn (fn [req] (success (:uri req))))]
    (testing "valid URL"
      (is (= (-> {:request-line "gemini://example.com"}
                 (handler)
                 (get-body)
                 (str))
             "gemini://example.com"))

      (is (instance? URI
                     (-> {:request-line "gemini://example.com"}
                         (handler)
                         (get-body)))))))

(deftest test-extract-path
  (let [logger (log/print-logger :fatal)
        middleware (extract-path :logger logger)
        handler (middleware (fn [req] (success (:full-path req))))]
    (testing "extract path from URI"
      (is (= (-> {:uri (java.net.URI. "gemini://example.com/path/to/resource")}
                 (handler)
                 (get-body))
             "/path/to/resource")))

    (testing "missing URI"
      (is (= (get-status (handler {}))
             40)))))

(deftest test-log-requests
  (let [logger (log/print-logger :fatal)
        middleware (log-requests :logger logger)
        handler (middleware (fn [req] req))]
    (testing "log request"
      (is (= (handler {:request-line "gemini://example.com"})
             {:request-line "gemini://example.com"})))))

(deftest test-log-responses
  (let [logger (log/print-logger :fatal)
        middleware (log-responses :logger logger)
        handler (middleware (fn [_] (success "response")))]
    (testing "log response"
      (is (= (get-body (handler {}))
             "response")))))

(deftest test-ensure-return
  (let [logger (log/print-logger :fatal)
        middleware (ensure-return :logger logger)
        handler (middleware (fn [_] (throw (Exception. "error"))))]
    (testing "ensure return on exception"
      (is (= (get-status (handler {}))
             40)))))
