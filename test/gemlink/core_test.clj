(ns gemlink.core-test
  (:require [gemlink.core :as sut]
            [clojure.test :refer [deftest is testing run-tests]]))

(run-tests)
(ns gemlink.core-test
  (:require [clojure.test :refer :all]
            [gemlink.core :refer :all]
            [clojure.string :as str])
  (:import (java.net Socket)
           (java.io ByteArrayInputStream ByteArrayOutputStream)))

(defn mock-socket [input]
  (let [in-stream (ByteArrayInputStream. (.getBytes input))
        out-stream (ByteArrayOutputStream.)]
    (proxy [Socket] []
      (getInputStream [] in-stream)
      (getOutputStream [] out-stream)
      (close [] nil)
      (getInetAddress [] (proxy [java.net.InetAddress] []))
      (getPort [] 12345)
      (getLocalPort [] 12345)
      (getSession [] (proxy [javax.net.ssl.SSLSession] []
                       (getProtocol [] "TLSv1.2")
                       (getCipherSuite [] "TLS_RSA_WITH_AES_128_CBC_SHA"))))))

(deftest test-base-handler
  (let [logger (atom [])
        log-fn (fn [level msg] (swap! logger conj [level msg]))
        handler (base-handler (fn [_] (success "Hello, Gemini!")) {:logger log-fn})
        socket (mock-socket "gemini://example.com\r\n")]
    (handler socket)
    (let [output (str/join "\n" @logger)]
      (is (str/includes? output "request: gemini://example.com"))
      (is (str/includes? output "20 text/gemini"))
      (is (str/includes? output "Hello, Gemini!")))))
