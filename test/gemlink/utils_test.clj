(ns gemlink.utils-test
  (:require [gemlink.utils :refer :all]
            [gemlink.path :refer [join-paths string->path split-path]]
            [clojure.test :refer [deftest is testing]])
  (:import [java.nio.file Files Paths]))

(ns gemlink.utils-test
  (:require [clojure.test :refer [deftest is testing run-tests]]))

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
             [y 2]   (+ y 2)
             [z 3]   (+ z 3)
             :else 0)
           4)))

  (testing "destructuring"
    (is (= (cond-let
            [[x y] [2 3]] (* x y)
            :else 0)
           6))

    (is (= (cond-let
            [{:keys [x y]} {:x 5 :y 4}] (* x y)
            :else 0)
           20))))

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
    (is (= (parse-route-config [["/a/b" {:handler :handler}]])
           {"a" {:children {"b" {:handler :handler}}}}))
    (is (= (parse-route-config [["/a" {:handler :handler}]
                                ["/b" {:handler :handler2}]])
           {"a" {:handler :handler}
            "b" {:handler :handler2}}))
    (is (thrown? Exception
                 (parse-route-config [["/a"]])))
    (is (= (parse-route-config [["/a" {:handler :handler0}]
                                ["/b/c"
                                 ["/d/e" {:handler :handler3}]
                                 ["/f" {:handler :handler4}]]])
           {"a" {:handler :handler0}
            "b" {:children
                 {"c" {:children
                       {"d" {:children
                             {"e" {:handler :handler3}}}
                        "f" {:handler :handler4}}}}}}))))

(deftest test-generate-listing
  (testing "generate directory listing"
    (let [temp-dir (make-temp-directory "test-dir")
          file1 (create-file (string->path (join-paths (str temp-dir) "file1.txt")))
          file2 (create-file (string->path (join-paths (str temp-dir) "file2.txt")))
          subdir (create-directory (Paths/get (str temp-dir)
                                              (into-array String ["subdir"])))]
      (try
        (let [base-uri (java.net.URI. "gemini://example.com/")
              listing (generate-listing base-uri (.toString temp-dir))]
          (is (string? listing))
          (is (re-find #"file1.txt" listing))
          (is (re-find #"file2.txt" listing))
          (is (re-find #"subdir" listing)))
        (finally
          (Files/delete file1)
          (Files/delete file2)
          (Files/delete subdir)
          (Files/delete temp-dir))))))
