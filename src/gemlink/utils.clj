(ns gemlink.utils)

(defmacro cond-let
  [& clauses]
  (when (odd? (count clauses))
    (throw (IllegalArgumentException. "cond-let requires an even number of forms")))
  (letfn [(expand [[test then & more]]
            (cond
              (and (= test :else) (empty? more)) then

              (and (empty? more) (not= test :else))
              (throw (IllegalArgumentException. "cond-let requires an :else clause when no conditions match"))

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
