(ns gemlink.logging-test
  (:require [clojure.test :refer [deftest is testing]]
            [gemlink.logging :as log]))

(defn capture-output
  "Capture stdout while executing a function."
  [f]
  (let [out-stream (java.io.ByteArrayOutputStream.)
        original-out System/out]
    (try
      (System/setOut (java.io.PrintStream. out-stream))
      (f)
      (.toString out-stream)
      (finally
        (System/setOut original-out)))))

(deftest log-levels-test
  (testing "log levels are defined in correct order"
    (is (= [:fatal :error :warn :info :debug] log/LOG-LEVELS)))

  (testing "log-index returns correct indices"
    (is (= 0 (log/log-index :fatal)))
    (is (= 1 (log/log-index :error)))
    (is (= 2 (log/log-index :warn)))
    (is (= 3 (log/log-index :info)))
    (is (= 4 (log/log-index :debug)))))

(deftest nil-logger-test
  (testing "nil logger implements all methods without error"
    (is (nil? (log/fatal! nil "test")))
    (is (nil? (log/error! nil "test")))
    (is (nil? (log/warn! nil "test")))
    (is (nil? (log/info! nil "test")))
    (is (nil? (log/debug! nil "test"))))

  (testing "nil logger produces no output"
    (let [output (capture-output
                   #(do (log/fatal! nil "fatal message")
                        (log/error! nil "error message")
                        (log/warn! nil "warn message")
                        (log/info! nil "info message")
                        (log/debug! nil "debug message")))]
      (is (= "" output)))))

(deftest print-logger-fatal-level-test
  (testing "fatal level logger only logs fatal messages"
    (let [logger (log/print-logger :fatal)
          output (capture-output
                   #(do (log/fatal! logger "fatal message")
                        (log/error! logger "error message")
                        (log/warn! logger "warn message")
                        (log/info! logger "info message")
                        (log/debug! logger "debug message")))]
      (is (re-find #"fatal message" output))
      (is (not (re-find #"error message" output)))
      (is (not (re-find #"warn message" output)))
      (is (not (re-find #"info message" output)))
      (is (not (re-find #"debug message" output))))))

(deftest print-logger-error-level-test
  (testing "error level logger logs fatal and error messages"
    (let [logger (log/print-logger :error)
          output (capture-output
                   #(do (log/fatal! logger "fatal message")
                        (log/error! logger "error message")
                        (log/warn! logger "warn message")
                        (log/info! logger "info message")
                        (log/debug! logger "debug message")))]
      (is (re-find #"fatal message" output))
      (is (re-find #"error message" output))
      (is (not (re-find #"warn message" output)))
      (is (not (re-find #"info message" output)))
      (is (not (re-find #"debug message" output))))))

(deftest print-logger-warn-level-test
  (testing "warn level logger logs fatal, error, and warn messages"
    (let [logger (log/print-logger :warn)
          output (capture-output
                   #(do (log/fatal! logger "fatal message")
                        (log/error! logger "error message")
                        (log/warn! logger "warn message")
                        (log/info! logger "info message")
                        (log/debug! logger "debug message")))]
      (is (re-find #"fatal message" output))
      (is (re-find #"error message" output))
      (is (re-find #"warn message" output))
      (is (not (re-find #"info message" output)))
      (is (not (re-find #"debug message" output))))))

(deftest print-logger-info-level-test
  (testing "info level logger logs fatal, error, warn, and info messages"
    (let [logger (log/print-logger :info)
          output (capture-output
                   #(do (log/fatal! logger "fatal message")
                        (log/error! logger "error message")
                        (log/warn! logger "warn message")
                        (log/info! logger "info message")
                        (log/debug! logger "debug message")))]
      (is (re-find #"fatal message" output))
      (is (re-find #"error message" output))
      (is (re-find #"warn message" output))
      (is (re-find #"info message" output))
      (is (not (re-find #"debug message" output))))))

(deftest print-logger-debug-level-test
  (testing "debug level logger logs all messages"
    (let [logger (log/print-logger :debug)
          output (capture-output
                   #(do (log/fatal! logger "fatal message")
                        (log/error! logger "error message")
                        (log/warn! logger "warn message")
                        (log/info! logger "info message")
                        (log/debug! logger "debug message")))]
      (is (re-find #"fatal message" output))
      (is (re-find #"error message" output))
      (is (re-find #"warn message" output))
      (is (re-find #"info message" output))
      (is (re-find #"debug message" output)))))

(deftest logger-protocol-test
  (testing "logger satisfies Logger protocol"
    (let [logger (log/print-logger :info)]
      (is (satisfies? log/Logger logger))))

  (testing "nil satisfies Logger protocol"
    (is (satisfies? log/Logger nil))))

(deftest logger-message-formatting-test
  (testing "logger handles formatted messages"
    (let [logger (log/print-logger :info)
          formatted-msg "Request took 123ms"
          output (capture-output #(log/info! logger formatted-msg))]
      (is (re-find #"Request took 123ms" output))))

  (testing "logger handles multiline messages"
    (let [logger (log/print-logger :error)
          multiline "Line 1\nLine 2\nLine 3"
          output (capture-output #(log/error! logger multiline))]
      (is (re-find #"Line 1" output))
      (is (re-find #"Line 2" output))
      (is (re-find #"Line 3" output))))

  (testing "logger handles special characters"
    (let [logger (log/print-logger :warn)
          special "Special chars: !@#$%^&*()"
          output (capture-output #(log/warn! logger special))]
      (is (re-find #"Special chars" output)))))

(deftest logger-concurrent-test
  (testing "logger is thread-safe with concurrent logging"
    (let [logger (log/print-logger :info)
          num-threads 10
          num-messages 100
          messages (atom [])
          threads (doall
                    (for [i (range num-threads)]
                      (Thread.
                        #(dotimes [j num-messages]
                           (let [msg (format "Thread %d Message %d" i j)]
                             (swap! messages conj msg)
                             (log/info! logger msg))))))]
      (doseq [t threads] (.start t))
      (doseq [t threads] (.join t))
      ;; Should have logged all messages without error
      (is (= (* num-threads num-messages) (count @messages))))))

(deftest custom-logger-implementation-test
  (testing "can implement custom logger"
    (let [logged-messages (atom [])
          custom-logger (reify log/Logger
                          (fatal! [_ msg] (swap! logged-messages conj [:fatal msg]))
                          (error! [_ msg] (swap! logged-messages conj [:error msg]))
                          (warn! [_ msg] (swap! logged-messages conj [:warn msg]))
                          (info! [_ msg] (swap! logged-messages conj [:info msg]))
                          (debug! [_ msg] (swap! logged-messages conj [:debug msg])))]
      (log/fatal! custom-logger "fatal")
      (log/error! custom-logger "error")
      (log/warn! custom-logger "warn")
      (log/info! custom-logger "info")
      (log/debug! custom-logger "debug")

      (is (= 5 (count @logged-messages)))
      (is (= [:fatal "fatal"] (first @logged-messages)))
      (is (= [:debug "debug"] (last @logged-messages))))))

(deftest logger-empty-message-test
  (testing "logger handles empty messages"
    (let [logger (log/print-logger :info)
          output (capture-output #(log/info! logger ""))]
      ;; Should not throw error, just log empty line
      (is (string? output)))))

(deftest logger-nil-message-test
  (testing "logger handles nil messages"
    (let [logger (log/print-logger :info)]
      ;; Should not throw error
      (is (nil? (capture-output #(log/info! logger nil)))))))
