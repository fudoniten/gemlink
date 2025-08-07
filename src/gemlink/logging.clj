(ns gemlink.logging)

(defprotocol Logger
  (fatal! [self msg])
  (error! [self msg])
  (warn!  [self msg])
  (info!  [self msg])
  (debug! [self msg]))

(def LOG-LEVELS [:fatal :error :warn :info :debug])
(defn log-index [log-level] (.indexOf LOG-LEVELS log-level))

(extend-protocol Logger
  nil
  (fatal! [_ _])
  (error! [_ _])
  (warn!  [_ _])
  (info!  [_ _])
  (debug! [_ _]))

(defn print-logger
  [log-level]
  (let [log-idx (log-index log-level)]
    (reify Logger

      (fatal! [_ msg]
        (when (<= (log-index :fatal) log-idx)
          (println msg)
          (flush)))

      (error! [_ msg]
        (when (<= (log-index :error) log-idx)
          (println msg)
          (flush)))

      (warn! [_ msg]
        (when (<= (log-index :warn) log-idx)
          (println msg)
          (flush)))

      (info! [_ msg]
        (when (<= (log-index :info) log-idx)
          (println msg)
          (flush)))

      (debug! [_ msg]
        (when (<= (log-index :debug) log-idx)
          (println msg)
          (flush))))))
