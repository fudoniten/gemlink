(ns gemlink.core-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]

            [gemlink.core :refer :all]
            [gemlink.logging :as log]
            [gemlink.handlers :refer [base-handler]]
            [gemlink.response :refer [success bad-request-error not-authorized-error get-type get-body]])
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
        logger (log/print-logger :fatal)
        handler (base-handler (fn [_] (success "Hello, Gemini!")) {:logger logger})
        socket (mock-socket "gemini://example.com\r\n" out-stream)]
    (testing "successfully request simple page"
      (handler socket)
      (let [output (.toString out-stream)
            lines (str/split output #"\r\n")]
        (is (= (first lines) "20 text/gemini"))
        (is (= (second lines) "Hello, Gemini!")))))

  (let [out-stream (ByteArrayOutputStream.)
        logger (log/print-logger :fatal)
        handler (base-handler (fn [_] (bad-request-error "Invalid request")) {:logger logger})
        socket (mock-socket "invalid-request\r\n" out-stream)]
    (testing "handle invalid request"
      (handler socket)
      (let [output (.toString out-stream)
            lines (str/split output #"\r\n")]
        (is (= (first lines) "59 bad request"))
        (is (= (second lines) "Invalid request")))))

  (let [out-stream (ByteArrayOutputStream.)
        logger (log/print-logger :fatal)
        handler (base-handler (fn [_] (throw (Exception. "Handler error"))) {:logger logger})
        socket (mock-socket "gemini://example.com\r\n" out-stream)]
    (testing "handle handler error"
      (handler socket)
      (let [output (.toString out-stream)
            lines (str/split output #"\r\n")]
        (is (= (first lines) "40 unknown server error")))))

  (let [out-stream (ByteArrayOutputStream.)
        logger (log/print-logger :fatal)
        handler (base-handler (fn [_] "Not a response") {:logger logger})
        socket (mock-socket "gemini://example.com\r\n" out-stream)]
    (testing "handle unknown handler error"
      (handler socket)
      (let [output (.toString out-stream)
            lines (str/split output #"\r\n")]
        (is (= (first lines) "40 unknown handler error"))))))

(deftest test-define-routes
  (let [matcher (-> (define-routes {} [["/test" {:handler (fn [_] :success)}]]))]

    (testing "matching route"
      (let [req {:remaining-path ["test"]}
            result (matcher req)]
        (is (= result :success))))

    (testing "non-matching route"
      (let [req {:remaining-path ["other" "path"]}]
        (is (= (get-type (matcher req)) :not-found-error)))))

  (let [matcher (-> (define-routes {} [["/test"
                                        ["/some" {:handler (fn [_] :success)}]
                                        ["/other" {:handler (fn [_] :success2)}]
                                        ["/return-path" {:handler (fn [{:keys [remaining-path]}] remaining-path)}]]]))]

    (testing "nested matching route"
      (let [req {:remaining-path ["test" "some"]}]
        (is (= (matcher req) :success)))
      (let [req {:remaining-path ["test" "other"]}]
        (is (= (matcher req) :success2))))

    (testing "missing sub path"
      (let [req {:remaining-path ["nonexistent"]}]
        (is (= (get-type (matcher req)) :not-found-error)))
      (let [req {:remaining-path ["test" "nonexistent"]}]
        (is (= (get-type (matcher req)) :not-found-error))))

    (testing "incomplete path"
      (let [req {:remaining-path ["test"]}]
        (is (= (get-type (matcher req)) :not-found-error))))

    (testing "path is passed to handler"
      (let [req {:remaining-path ["test" "return-path" "one" "two"]}]
        (is (= (matcher req) "/one/two")))))

  (let [matcher (-> (define-routes {} [["/test/:one" {:handler (fn [req] req)}]]))]

    (testing "assign-parameter"
      (let [req {:remaining-path ["test" "something"]}]
        (is (= (-> (matcher req) :params :one)
               "something"))))

    (testing "assign-parameter-with-path"
      (let [req {:remaining-path ["test" "something" "other"]}
            resp (matcher req)]
        (is (= (-> resp :params :one)
               "something"))
        (is (= (-> resp :remaining-path)
               "/other")))))

  (let [matcher (-> (define-routes {} [["/test/:one/sub/:two" {:handler (fn [req] req)}]]))]

    (testing "assign-multiple-parameters"
      (let [req {:remaining-path ["test" "something" "sub" "else"]}
            resp (matcher req)]
        (is (= (-> resp :params)
               {:one "something" :two "else"}))))

    (testing "missing after parameter"
      (let [req {:remaining-path ["test" "something" "oops"]}]
        (is (= (get-type (matcher req)) :not-found-error)))))

  (let [matcher (-> (define-routes {} [["/test"
                                        ["/one" {:middleware [(fn [handler] (fn [req] (handler (assoc req :addition :one))))]}
                                         ["/two" {:handler (fn [req] req)}]]
                                        ["/other" {:handler (fn [_] :alternative)}]]]))]
    (testing "middleware applied"
      (let [req {:remaining-path ["test" "one" "two"]}]
        (is (= (-> req (matcher) :addition) :one))))

    (testing "middleware not applied on different paths"
      (let [req {:remaining-path ["test" "other"]}]
        (is (= (matcher req) :alternative))

        (is (nil? (some-> req (matcher) :addition))))))

  (let [matcher (-> (define-routes {} [["/test"
                                        ["/one" {:middleware [(fn [handler] (fn [req] (handler (assoc req :tester [:first]))))]}
                                         ["/two" {:middleware [(fn [handler] (fn [req] (handler (update req :tester (fn [arr] (conj arr :second))))))]}
                                          ["/three" {:handler (fn [req] req)}]]]
                                        ["/other" {:handler (fn [_] :alternative)}]]]))]
    (testing "middleware applied in order"
      (let [req {:remaining-path ["test" "one" "two" "three"]}]
        (is (= (-> req (matcher) :tester) [:first :second])))))

  (let [matcher (-> (define-routes {} [["/test"
                                        ["/one" {:middleware [(fn [_] (fn [_] (not-authorized-error "oops")))]}
                                         ["/two" {:handler (fn [req] req)}]]
                                        ["/other" {:handler (fn [_] :alternative)}]]]))]
    (testing "middleware not applied on different paths"
      (let [req {:remaining-path ["test" "one" "two"]}]
        (is (= (get-type (matcher req)) :not-authorized-error))))))

(deftest test-fold-middleware
  (let [middleware1 (fn [handler]
                      (fn [req]
                        (handler (update req :mw (fn [val] (concat val [:mw1]))))))
        middleware2 (fn [handler]
                      (fn [req]
                        (handler (update req :mw (fn [val] (concat val [:mw2]))))))
        handler (fn [req] (success req))]

    (testing "no middleware"
      (let [mw-fn (fold-middleware)
            wrapped-handler (mw-fn handler)]
        (is (= (get-body (wrapped-handler {})) {}))))

    (testing "single middleware"
      (let [mw-fn (fold-middleware middleware1)
            wrapped-handler (mw-fn handler)]
        (is (= (:mw (get-body (wrapped-handler {}))) '(:mw1)))))

    (testing "multiple middleware"
      (let [mw-fn (fold-middleware middleware1 middleware2)
            wrapped-handler (mw-fn handler)]
        (is (= (:mw (get-body (wrapped-handler {}))) '(:mw1 :mw2))))

      (let [mw-fn (fold-middleware middleware2 middleware1)
            wrapped-handler (mw-fn handler)]
        (is (= (:mw (get-body (wrapped-handler {}))) '(:mw2 :mw1)))))))

(run-tests)
