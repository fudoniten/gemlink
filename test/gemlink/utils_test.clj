(ns gemlink.utils-test
  (:require [gemlink.utils :refer :all]
            [clojure.test :refer [deftest is testing run-tests]]))

(ns gemlink.utils-test
  (:require [clojure.test :refer [deftest is testing run-tests]]))

(deftest test-cond-let
  (testing "basic usage"
    (is (= (cond-let
             [x 1] (+ x 1)
             [y 2] (+ y 2)
             :else 0)
           2))))

  (testing "else clause"
    (is (= (cond-let
             [x nil] (+ x 1)
             [y nil] (+ y 2)
             :else 42)
           42)))

  (testing "first match"
    (is (= (cond-let
             [x nil] (+ x 1)
             [y 2]   (+ y 2)
             [z 3]   (+ z 3)
             :else 0)
           4)))

(deftest test-pretty-format
  (testing "pretty-format"
    (is (string? (pretty-format {:a 1 :b 2})))
    (is (re-find #":a 1" (pretty-format {:a 1 :b 2})))))

(deftest test-split-path
  (testing "split-path"
    (is (= (split-path "/a/b/c") ["a" "b" "c"]))
    (is (= (split-path "/a//b/c/") ["a" "b" "c"]))
    (is (= (split-path "") []))))

(deftest test-parse-route-config
  (testing "parse-route-config"
    (is (= (parse-route-config "/a/b" {:handler :handler})
           {"a" {"b" {:handler :handler}}}))
    (is (= (parse-route-config "/a" {:handler :handler} "/b" {:handler :handler2})
           {"a" {:handler :handler, :children ({"b" {:handler :handler2}})}}))))

(run-tests)
