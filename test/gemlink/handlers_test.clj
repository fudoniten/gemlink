(ns gemlink.handlers-test
  (:require [clojure.test :refer [deftest is testing]]
            [taoensso.timbre :as log]
            [gemlink.handlers :as handlers]
            [gemlink.response :as response])
  (:import [java.io File]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]
           [java.net URI]))

(defn create-temp-dir
  "Create a temporary directory for testing."
  []
  (str (Files/createTempDirectory "gemlink-handlers-test" (into-array FileAttribute []))))

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

(deftest static-handler-test
  (testing "returns static content"
    (let [content "Hello, Gemini!"
          handler (handlers/static-handler content)
          request {:uri (URI. "gemini://example.com/test")}
          response (handler request)]
      (is (response/response? response))
      (is (= 20 (response/get-status response)))
      (is (= content (response/get-body response)))))

  (testing "works with different content types"
    (let [gemtext "# Title\n=> link\n* item"
          handler (handlers/static-handler gemtext)
          request {}
          response (handler request)]
      (is (response/response? response))
      (is (= gemtext (response/get-body response)))))

  (testing "ignores request details"
    (let [content "static"
          handler (handlers/static-handler content)
          request1 {:uri (URI. "gemini://example.com/path1")}
          request2 {:uri (URI. "gemini://example.com/path2")}]
      (is (= (response/get-body (handler request1))
             (response/get-body (handler request2)))))))

(deftest path-handler-test
  (let [temp-dir (create-temp-dir)]
    (try
      (testing "serves existing file"
        (let [content "Test file content"
              file-path "test.txt"
              _ (create-temp-file temp-dir file-path content)
              handler (handlers/path-handler temp-dir {})
              request {:uri (URI. (str "gemini://example.com/" file-path))
                       :remaining-path [file-path]}
              response (handler request)]
          (is (response/response? response))
          (is (= 20 (response/get-status response)))
          (is (= content (response/get-body response)))))

      (testing "returns 404 for non-existent file"
        (let [handler (handlers/path-handler temp-dir {})
              request {:uri (URI. "gemini://example.com/nonexistent.txt")
                       :remaining-path ["nonexistent.txt"]}
              response (handler request)]
          (is (response/response? response))
          (is (= 51 (response/get-status response)))))

      (testing "detects MIME types correctly"
        (let [gmi-content "# Gemini page"
              _ (create-temp-file temp-dir "page.gmi" gmi-content)
              handler (handlers/path-handler temp-dir {})
              request {:uri (URI. "gemini://example.com/page.gmi")
                       :remaining-path ["page.gmi"]}
              response (handler request)]
          (is (= "text/gemini" (response/get-header response)))))

      (testing "handles nested paths"
        (.mkdir (File. temp-dir "subdir"))
        (let [content "Nested file"
              _ (create-temp-file (str temp-dir "/subdir") "nested.txt" content)
              handler (handlers/path-handler temp-dir {})
              request {:uri (URI. "gemini://example.com/subdir/nested.txt")
                       :remaining-path ["subdir" "nested.txt"]}
              response (handler request)]
          (is (= 20 (response/get-status response)))
          (is (= content (response/get-body response)))))

      (testing "prevents directory traversal"
        (let [handler (handlers/path-handler temp-dir {})
              request {:uri (URI. "gemini://example.com/../../../etc/passwd")
                       :remaining-path [".." ".." ".." "etc" "passwd"]}
              response (handler request)]
          (is (response/response? response))
          ;; Should return bad request (59) or not found (51) for security
          (is (or (= 59 (response/get-status response))
                  (= 51 (response/get-status response))))))

      (testing "serves index file when configured"
        (let [index-content "# Index Page"
              _ (create-temp-file temp-dir "index.gmi" index-content)
              handler (handlers/path-handler temp-dir {:index-file "index.gmi"})
              request {:uri (URI. "gemini://example.com/")
                       :remaining-path nil}
              response (handler request)]
          (is (= 20 (response/get-status response)))
          (is (= index-content (response/get-body response)))))

      (testing "returns 404 when no index file configured"
        (let [handler (handlers/path-handler temp-dir {})
              request {:uri (URI. "gemini://example.com/")
                       :remaining-path nil}
              response (handler request)]
          (is (= 51 (response/get-status response)))))

      (finally
        (delete-recursively temp-dir)))))

(deftest users-handler-test
  (testing "routes to correct user handler"
    (let [alice-handler (fn [req] (response/success "Alice's page"))
          bob-handler (fn [req] (response/success "Bob's page"))
          mapper {"alice" alice-handler
                  "bob" bob-handler}
          handler (handlers/users-handler mapper)
            alice-request {:uri (URI. "gemini://example.com/alice/profile")
                          :remaining-path ["alice" "profile"]}
            bob-request {:uri (URI. "gemini://example.com/bob/profile")
                        :remaining-path ["bob" "profile"]}]
        (is (= "Alice's page" (response/get-body (handler alice-request))))
        (is (= "Bob's page" (response/get-body (handler bob-request))))))

    (testing "returns 404 for non-existent user"
      (let [mapper {"alice" (fn [req] (response/success "Alice"))}
            handler (handlers/users-handler mapper )
            request {:uri (URI. "gemini://example.com/bob/profile")
                     :remaining-path ["bob" "profile"]}
            response (handler request)]
        (is (= 51 (response/get-status response)))))

    (testing "passes remaining path to user handler"
      (let [received-path (atom nil)
            user-handler (fn [req]
                          (reset! received-path (:remaining-path req))
                          (response/success "OK"))
            mapper {"alice" user-handler}
            handler (handlers/users-handler mapper )
            request {:uri (URI. "gemini://example.com/alice/sub/path")
                     :remaining-path ["alice" "sub" "path"]}]
        (handler request)
        (is (= "/sub/path" @received-path))))

    (testing "handles user with no additional path"
      (let [user-handler (fn [req] (response/success "User home"))
            mapper {"alice" user-handler}
            handler (handlers/users-handler mapper )
            request {:uri (URI. "gemini://example.com/alice")
                     :remaining-path ["alice"]}
            response (handler request)]
        (is (= 20 (response/get-status response)))
        (is (= "User home" (response/get-body response)))))

    (testing "handles empty user"
      (let [mapper {"alice" (fn [req] (response/success "Alice"))}
            handler (handlers/users-handler mapper )
            request {:uri (URI. "gemini://example.com/")
                     :remaining-path []}
            response (handler request)]
        ;; Should return 404 for empty user
        (is (= 51 (response/get-status response))))))

(deftest handler-integration-test
  (testing "static handler always returns same content"
    (let [content "Static content"
          handler (handlers/static-handler content)]
      (dotimes [_ 10]
        (is (= content (response/get-body (handler {})))))))

  (testing "path handler with multiple files"
    (let [temp-dir (create-temp-dir)]
      (try
        ;; Create multiple files
        (create-temp-file temp-dir "file1.txt" "Content 1")
        (create-temp-file temp-dir "file2.gmi" "# Content 2")
        (create-temp-file temp-dir "file3.html" "<h1>Content 3</h1>")

        (let [handler (handlers/path-handler temp-dir {})]
          (is (= "Content 1"
                 (response/get-body
                   (handler {:uri (URI. "gemini://example.com/file1.txt")
                            :remaining-path ["file1.txt"]}))))

          (is (= "# Content 2"
                 (response/get-body
                   (handler {:uri (URI. "gemini://example.com/file2.gmi")
                            :remaining-path ["file2.gmi"]}))))

          (is (= "<h1>Content 3</h1>"
                 (response/get-body
                   (handler {:uri (URI. "gemini://example.com/file3.html")
                            :remaining-path ["file3.html"]})))))

        (finally
          (delete-recursively temp-dir)))))

  (testing "users handler with nested user handlers"
    (let [alice-files (create-temp-dir)]
      (try
        (create-temp-file alice-files "about.txt" "About Alice")

        (let [alice-handler (handlers/path-handler alice-files {})
              mapper {"alice" alice-handler}
              users-handler (handlers/users-handler mapper )
              request {:uri (URI. "gemini://example.com/alice/about.txt")
                       :remaining-path ["alice" "about.txt"]}
              response (users-handler request)]
          (is (= 20 (response/get-status response)))
          (is (= "About Alice" (response/get-body response))))

        (finally
          (delete-recursively alice-files))))))
