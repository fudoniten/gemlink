(ns gemlink.middleware-test
  (:require [gemlink.middleware :refer :all]
            [clojure.test :refer [deftest is testing run-tests]]))

(deftest test-parse-url
  (let [handler (parse-url (fn [req] (:uri req)))]
    (testing "valid URL"
      (is (= (str (handler {:request-line "gemini://example.com"}))
             "gemini://example.com")))

    (testing "invalid URL"
      (is (= (get-status (handler {:request-line "invalid-url"}))
             59)))))

(deftest test-extract-path
  (let [logger (log/print-logger :debug)
        handler (extract-path :logger logger (fn [req] (:full-path req)))]
    (testing "extract path from URI"
      (is (= (handler {:uri (java.net.URI. "gemini://example.com/path/to/resource")})
             "/path/to/resource")))

    (testing "missing URI"
      (is (= (get-status (handler {}))
             40)))))

(deftest test-log-requests
  (let [logger (log/print-logger :debug)
        handler (log-requests :logger logger (fn [req] req))]
    (testing "log request"
      (is (= (handler {:request-line "gemini://example.com"})
             {:request-line "gemini://example.com"})))))

(deftest test-log-responses
  (let [logger (log/print-logger :debug)
        handler (log-responses :logger logger (fn [req] (success "response")))]
    (testing "log response"
      (is (= (get-body (handler {}))
             "response")))))

(deftest test-ensure-return
  (let [logger (log/print-logger :debug)
        handler (ensure-return :logger logger (fn [_] (throw (Exception. "error"))))]
    (testing "ensure return on exception"
      (is (= (get-status (handler {}))
             40)))))

(run-tests)
