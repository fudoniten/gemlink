(ns gemlink.logging)

(defprotocol Logger
  (fatal! [self msg])
  (error! [self msg])
  (warn!  [self msg])
  (info!  [self msg])
  (debug! [self msg]))

(def LOG-LEVELS [:fatal :error :warn :info :debug])
(defn log-index [log-level] (.indexOf LOG-LEVELS log-level))

(defn print-logger
  [log-level]
  (let [log-idx (log-index log-level)]
    (reify Logger

      (fatal! [_ msg]
        (when (<= (log-index :fatal) log-idx)
          (println msg)))

      (error! [_ msg]
        (when (<= (log-index :error) log-idx)
          (println msg)))

      (warn! [_ msg]
        (when (<= (log-index :warn) log-idx)
          (println msg)))

      (info! [_ msg]
        (when (<= (log-index :info) log-idx)
          (println msg)))

      (debug! [_ msg]
        (when (<= (log-index :debug) log-idx)
          (println msg))))))
