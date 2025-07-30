(ns gemlink.core-test
  (:require [gemlink.core :refer :all]
            [gemlink.logging :as log]
            [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str])
  (:import (java.net InetAddress)
           (java.io ByteArrayInputStream ByteArrayOutputStream)
           (javax.net.ssl SSLSocket)))

(defn mock-socket [input out-stream]
  (let [in-stream (ByteArrayInputStream. (.getBytes input))]
    (proxy [SSLSocket] []
      (getInputStream [] in-stream)
      (getOutputStream [] out-stream)
      (close [] nil)
      (startHandshake [] nil)
      (shutdownOutput [] nil)
      (getInetAddress [] (InetAddress/getByName "127.0.0.1"))
      (getPort [] 12345)
      (getLocalPort [] 12345)
      (getSession [] (proxy [javax.net.ssl.SSLSession] []
                       (getProtocol [] "TLSv1.2")
                       (getCipherSuite [] "TLS_RSA_WITH_AES_128_CBC_SHA"))))))

(deftest test-base-handler
  (let [out-stream (ByteArrayOutputStream.)
        logger (log/print-logger :debug)
        handler (base-handler (fn [_] (success "Hello, Gemini!")) {:logger logger})
        socket (mock-socket "gemini://example.com\r\n" out-stream)]
    (testing "successfully request simple page"
      (handler socket)
      (let [output (.toString out-stream)
            lines (str/split output #"\r\n")]
        (is (= (first lines) "20 text/gemini"))
        (is (= (second lines) "Hello, Gemini!")))))

  (let [out-stream (ByteArrayOutputStream.)
        logger (log/print-logger :debug)
        handler (base-handler (fn [_] (bad-request-error "Invalid request")) {:logger logger})
        socket (mock-socket "invalid-request\r\n" out-stream)]
    (testing "handle invalid request"
      (handler socket)
      (let [output (.toString out-stream)
            lines (str/split output #"\r\n")]
        (is (= (first lines) "59 bad request"))
        (is (= (second lines) "Invalid request")))))

  (let [out-stream (ByteArrayOutputStream.)
        logger (log/print-logger :debug)
        handler (base-handler (fn [_] (throw (Exception. "Handler error"))) {:logger logger})
        socket (mock-socket "gemini://example.com\r\n" out-stream)]
    (testing "handle handler error"
      (handler socket)
      (let [output (.toString out-stream)
            lines (str/split output #"\r\n")]
        (is (= (first lines) "40 unknown server error")))))

  (let [out-stream (ByteArrayOutputStream.)
        logger (log/print-logger :debug)
        handler (base-handler (fn [_] "Not a response") {:logger logger})
        socket (mock-socket "gemini://example.com\r\n" out-stream)]
    (testing "handle unknown handler error"
      (handler socket)
      (let [output (.toString out-stream)
            lines (str/split output #"\r\n")]
        (is (= (first lines) "40 unknown handler error"))))))

(deftest test-route-matcher
  (let [matcher (route-matcher "/test")]

    (testing "matching route"
      (let [req {:remaining-path "/test/path"}
            result (matcher req)]
        (is (= (:remaining-path result) "/path"))))

    (testing "non-matching route"
      (let [req {:remaining-path "/other/path"}
            result (matcher req)]
        (is (nil? result))))

    (testing "nil remaining-path"
      (let [req {:remaining-path nil}]
        (is (thrown? Exception (matcher req)))))))

(run-tests)
