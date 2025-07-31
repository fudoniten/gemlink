(ns gemlink.utils-test
  (:require [gemlink.utils :refer :all]
            [clojure.test :refer [deftest is testing run-tests]]))

(deftest test-cond-let
  (testing "basic usage"
    (is (= (cond-let
             [x 1] (+ x 1)
             [y 2] (+ y 2)
             :else 0)
           2)))

  (testing "else clause"
    (is (= (cond-let
             [x nil] (+ x 1)
             [y nil] (+ y 2)
             :else 42)
           42)))

  (testing "first match"
    (is (= (cond-let
             [x nil] (+ x 1)
             [y 2] (+ y 2)
             [z 3] (+ z 3)
             :else 0)
           4)))

  (testing "no match"
    (is (thrown? IllegalArgumentException
                 (cond-let
                   [x nil] (+ x 1)
                   [y nil] (+ y 2))))))

(run-tests)
