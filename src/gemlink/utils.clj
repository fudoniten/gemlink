(ns gemlink.utils)

(defmacro cond-let
  [& clauses]
  (when (odd? (count clauses))
    (throw (IllegalArgumentException. "cond-let requires an even number of forms")))
  (letfn [(expand [[test then & more]]
            (cond
              (and (= test :else) (empty? more)) then

              (vector? test)
              `(let [temp# ~(second test)]
                 (if temp#
                   (let [~(first test) temp#]
                     ~then)
                   ~(if (seq more)
                      (expand more)
                      (throw (IllegalArgumentException.
                              "failed to bind in bind-let")))))

              :else (throw (IllegalArgumentException.
                            (str "cond-let expected a binding vector, got " test)))))]
    (expand clauses)))
(defn pretty-format
  "Formats an object into a pretty-printed string."
  [o]
  (with-out-str (pprint o)))
(defn split-path
  "Splits a path string into a vector of non-empty segments."
  [path]
  (if (seq path)
    path
    (->> (str/split path #"/")
         (remove empty?)
         vec)))
(defn parse-route-config
  "Parses a route configuration into a nested map structure."
  [path & subroute-cfg]
  (let [[this & remaining] (split-path path)]
    ;; If this is a nested path (eg. /one/two) then the top path
    ;; element should just point at the next (with the same subroutes).
    (if remaining
      {this {:children (parse-route-config remaining subroute-cfg)}}
      ;; The first element after the current path MAY be a config map.
      ;; Otherwise, it'll be the first path.
      (let [[maybe-cfg & maybe-subroutes] subroute-cfg
            [cfg subroutes] (if (map? maybe-cfg)
                              [maybe-cfg maybe-subroutes]
                              [{} subroute-cfg])]
        {this (assoc cfg :children (map parse-route-config
                                        subroutes))}))))
