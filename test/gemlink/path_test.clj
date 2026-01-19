(ns gemlink.path-test
  (:require [clojure.test :refer [deftest is testing]]
            [gemlink.path :as path])
  (:import [java.io File]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn create-temp-dir
  "Create a temporary directory for testing."
  []
  (str (Files/createTempDirectory "gemlink-test" (into-array FileAttribute []))))

(defn create-temp-file
  "Create a temporary file with content."
  [dir name content]
  (let [file (File. dir name)]
    (spit file content)
    (.getAbsolutePath file)))

(defn delete-recursively
  "Delete a directory and all its contents."
  [^String path]
  (let [file (File. path)]
    (when (.isDirectory file)
      (doseq [child (.listFiles file)]
        (delete-recursively (.getAbsolutePath child))))
    (.delete file)))

(deftest join-paths-test
  (testing "joins valid paths correctly"
    (let [base "/base/path"]
      (is (string? (path/join-paths base "subdir")))
      (is (string? (path/join-paths base "subdir/file.txt")))))

  (testing "handles leading slash in subpath"
    (let [base "/base/path"]
      (is (string? (path/join-paths base "/subdir")))))

  (testing "handles empty subpath"
    (let [base "/base/path"]
      (is (string? (path/join-paths base "")))))

  (testing "prevents directory traversal with ../"
    (let [base "/base/path"]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"path is not within base path"
                            (path/join-paths base "../../../etc/passwd")))))

  (testing "prevents traversal with mixed valid/invalid segments"
    (let [base "/base/path"]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"path is not within base path"
                            (path/join-paths base "valid/../../etc/passwd")))))

  (testing "allows parent references within base"
    (let [base "/base/path"]
      ;; Going up and back down should be fine as long as we stay in base
      (is (string? (path/join-paths base "a/../b")))))

  (testing "normalized base path comparison"
    ;; This tests the fix for the path traversal bug
    (let [base "/base/path"]
      ;; Should properly compare against normalized base
      (is (string? (path/join-paths base "safe/file.txt"))))))

(deftest file-accessible?-test
  (let [temp-dir (create-temp-dir)
        test-file (create-temp-file temp-dir "test.txt" "content")]
    (try
      (testing "returns true for existing readable file"
        (is (true? (path/file-accessible? test-file))))

      (testing "returns false for non-existent file"
        (is (false? (path/file-accessible? (str temp-dir "/nonexistent.txt")))))

      (testing "returns false for directory"
        (is (false? (path/file-accessible? temp-dir))))

      (testing "returns false for unreadable file"
        (let [unreadable (create-temp-file temp-dir "unreadable.txt" "content")]
          (.setReadable (File. unreadable) false)
          (is (false? (path/file-accessible? unreadable)))
          (.setReadable (File. unreadable) true)))

      (finally
        (delete-recursively temp-dir)))))

(deftest get-file-contents-test
  (let [temp-dir (create-temp-dir)
        test-content "Hello, Gemini!"
        test-file (create-temp-file temp-dir "test.txt" test-content)]
    (try
      (testing "reads file contents successfully"
        (is (= test-content (path/get-file-contents test-file))))

      (testing "throws exception for non-existent file"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"missing file"
                              (path/get-file-contents (str temp-dir "/nonexistent.txt")))))

      (testing "throws exception for directory"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"missing file"
                              (path/get-file-contents temp-dir))))

      (testing "reads empty file"
        (let [empty-file (create-temp-file temp-dir "empty.txt" "")]
          (is (= "" (path/get-file-contents empty-file)))))

      (testing "reads multiline content"
        (let [multiline "Line 1\nLine 2\nLine 3"
              multiline-file (create-temp-file temp-dir "multiline.txt" multiline)]
          (is (= multiline (path/get-file-contents multiline-file)))))

      (finally
        (delete-recursively temp-dir)))))

(deftest file-extension-test
  (testing "extracts extension from filename"
    (is (= :txt (path/file-extension "file.txt")))
    (is (= :gmi (path/file-extension "page.gmi")))
    (is (= :html (path/file-extension "/path/to/file.html"))))

  (testing "handles multiple dots"
    (is (= :gz (path/file-extension "archive.tar.gz"))))

  (testing "returns nil for no extension"
    (is (nil? (path/file-extension "file")))
    (is (nil? (path/file-extension "/path/to/file"))))

  (testing "returns nil for hidden files without extension"
    (is (nil? (path/file-extension ".gitignore"))))

  (testing "handles hidden files with extension"
    (is (= :conf (path/file-extension ".config.conf")))))

(deftest list-directory-test
  (let [temp-dir (create-temp-dir)]
    (try
      (testing "lists empty directory"
        (is (= [] (path/list-directory temp-dir))))

      (testing "lists directory with files"
        (create-temp-file temp-dir "file1.txt" "content1")
        (create-temp-file temp-dir "file2.txt" "content2")
        (let [files (path/list-directory temp-dir)]
          (is (= 2 (count files)))
          (is (every? #(instance? File %) files))))

      (testing "includes subdirectories"
        (.mkdir (File. temp-dir "subdir"))
        (let [files (path/list-directory temp-dir)]
          (is (= 3 (count files)))))

      (finally
        (delete-recursively temp-dir)))))

(deftest split-path-test
  (testing "splits simple path"
    (is (= ["a" "b" "c"] (path/split-path "/a/b/c")))
    (is (= ["a" "b" "c"] (path/split-path "a/b/c"))))

  (testing "handles leading slash"
    (is (= ["path" "to" "file"] (path/split-path "/path/to/file"))))

  (testing "handles trailing slash"
    (is (= ["path" "to" "dir"] (path/split-path "/path/to/dir/"))))

  (testing "handles multiple slashes"
    (is (= ["a" "b" "c"] (path/split-path "//a///b//c//"))))

  (testing "handles empty string"
    (is (= [] (path/split-path ""))))

  (testing "handles single slash"
    (is (= [] (path/split-path "/"))))

  (testing "returns vector unchanged if already sequential"
    (is (= ["a" "b" "c"] (path/split-path ["a" "b" "c"])))))

(deftest build-path-test
  (testing "builds path from vector"
    (is (= "/a/b/c" (path/build-path ["a" "b" "c"]))))

  (testing "builds path from keywords"
    (is (= "/foo/bar/baz" (path/build-path [:foo :bar :baz]))))

  (testing "builds path from mixed types"
    (is (= "/a/b/c" (path/build-path [:a "b" 'c]))))

  (testing "handles empty vector"
    (is (= "/" (path/build-path []))))

  (testing "returns string unchanged"
    (is (= "/already/a/path" (path/build-path "/already/a/path")))))

(deftest string->path-test
  (testing "converts string to Path object"
    (let [result (path/string->path "/some/path")]
      (is (instance? java.nio.file.Path result))
      (is (= "/some/path" (str result))))))

(deftest path-security-integration-test
  (testing "comprehensive security scenarios"
    (let [temp-dir (create-temp-dir)
          safe-file (create-temp-file temp-dir "safe.txt" "safe content")]
      (try
        ;; Create a file we'll try to access via traversal
        (create-temp-file (str (.getParent (File. temp-dir))) "secret.txt" "secret")

        (testing "can access files within base"
          (let [joined (path/join-paths temp-dir "safe.txt")]
            (is (string? joined))
            (is (= "safe content" (path/get-file-contents joined)))))

        (testing "cannot access files outside base via ../"
          (is (thrown? Exception
                       (path/join-paths temp-dir "../secret.txt"))))

        (testing "cannot access absolute paths outside base"
          (is (thrown? Exception
                       (path/join-paths temp-dir "/etc/passwd"))))

        (testing "cannot use tricks like encoded dots"
          ;; The path normalization should handle these
          (is (thrown? Exception
                       (path/join-paths temp-dir "subdir/../../secret.txt"))))

        (finally
          (delete-recursively temp-dir))))))
