(ns gemlink.middleware-test
  (:require [gemlink.middleware :refer :all]
            [taoensso.timbre :as log]
            [gemlink.response :refer (get-status get-body success)]
            [clojure.test :refer [deftest is testing run-tests]])
  (:import java.net.URI))

(deftest test-parse-url
  (log/with-level :fatal
    (let [mw-fn (parse-url)
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
                           (get-body))))))))

(deftest test-extract-path
  (log/with-level :fatal
    (let [middleware (extract-path)
          handler (middleware (fn [req] (success (:full-path req))))]
      (testing "extract path from URI"
        (is (= (-> {:uri (java.net.URI. "gemini://example.com/path/to/resource")}
                   (handler)
                   (get-body))
               "/path/to/resource")))

      (testing "missing URI"
        (is (= (get-status (handler {}))
               40))))))

(deftest test-log-requests
  (log/with-level :fatal
    (let [middleware (log-requests)
          handler (middleware (fn [req] req))]
      (testing "log request"
        (is (= (handler {:request-line "gemini://example.com"})
               {:request-line "gemini://example.com"}))))))

(deftest test-log-responses
  (log/with-level :fatal
    (let [middleware (log-responses)
          handler (middleware (fn [_] (success "response")))]
      (testing "log response"
        (is (= (get-body (handler {}))
               "response"))))))

(deftest test-ensure-return
  (log/with-level :fatal
    (let [middleware (ensure-return)
          handler (middleware (fn [_] (throw (Exception. "error"))))]
      (testing "ensure return on exception"
        (is (= (get-status (handler {}))
               40))))))
