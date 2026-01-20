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
        (when (and msg (<= (log-index :fatal) log-idx))
          (.println System/out msg)
          (.flush System/out)))

      (error! [_ msg]
        (when (and msg (<= (log-index :error) log-idx))
          (.println System/out msg)
          (.flush System/out)))

      (warn! [_ msg]
        (when (and msg (<= (log-index :warn) log-idx))
          (.println System/out msg)
          (.flush System/out)))

      (info! [_ msg]
        (when (and msg (<= (log-index :info) log-idx))
          (.println System/out msg)
          (.flush System/out)))

      (debug! [_ msg]
        (when (and msg (<= (log-index :debug) log-idx))
          (.println System/out msg)
          (.flush System/out))))))
