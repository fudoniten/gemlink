(ns gemlink.gemtext
  (:require [clojure.string :as str]))

(defn normalize-node
  [node]
  (cond (vector? node)     node
        (sequential? node) (vec node)
        (string? node)     [:text node]
        (map? node)        (throw (ex-info "map is not supported in gemtext"
                                           {:node node}))
        :else              [:text (str node)]))

(defmulti render-node
  (fn [node]
    (cond (vector? node)     (first node)
          (sequential? node) :seq
          (string? node)     :string
          :else              :literal)))

(defmethod render-node :gemini
  [[_ & children]]
  (->> children
       (map render-node)
       (remove nil?)
       (str/join "\n")))

(defmethod render-node :text
  [[_ & parts]]
  (->> parts
       (map (fn [el]
              (if (vector? el)
                (render-node el)
                (str el))))
       (apply str)))

(defmethod render-node :h1
  [[_ text]]
  (str "# " text))

(defmethod render-node :h2
  [[_ text]]
  (str "## " text))

(defmethod render-node :h3
  [[_ text]]
  (str "### " text))

(defmethod render-node :link
  [[_ uri label]]
  (str "=> " uri (if label (str " " label) "")))

(defmethod render-node :list
  [[_ & items]]
  (->> items
       (map (fn [item]
              (str "* " (if (vector? item)
                          (render-node item)
                          item))))
       (str/join "\n")))

(defmethod render-node :quote
  [[_ text]]
  (str "> " text))

(defmethod render-node :pre
  [[_ & lines]]
  (str "```"
       \newline
       (str/join \newline lines)
       \newline
       "```"))

(defmethod render-node :footnote-block
  [[_ footnotes]]
  (str \newline
       (->> footnotes
            (map (fn [{:keys [n uri label]}]
                   (if uri
                     (str "=> " uri " [" n "] " label)
                     (str "[" n "] " label))))
            (str/join \newline))))

(defmethod render-node :seq
  [node]
  (render-node (normalize-node node)))

(defmethod render-node :string [s] s)

(defmethod render-node :literal [o] (str o))

(defmethod render-node :block
  [[_ & nodes]]
  (->> nodes (map render-node) (str/join \newline)))

(defn walk-footnotes
  [tree]
  (let [!counter (atom 0)
        !pending-footnotes (atom [])]
    (letfn [(flush-footnotes []
              (let [footnotes @!pending-footnotes]
                (reset! !pending-footnotes [])
                (when (seq footnotes)
                  [[:footnote-block footnotes]])))

            (walk-node [node]
              (cond (vector? node) (let [[tag & args] node]
                                     (if (= tag :footnote)
                                       (let [{:keys [uri text]} args
                                             n (swap! !counter inc)]
                                         (swap! !pending-footnotes
                                                conj {:n n
                                                      :uri uri
                                                      :label text})
                                         [:text (str "[" n "]")])

                                       (into [tag] (map walk-node args))))
                    :else node))

            (walk [nodes]
              (->> nodes
                   (mapcat
                    (fn [node]
                      ;; Headers flush the footnotes. Otherwise, proceed.
                      (if (and (vector? node) (#{:h1 :h2 :h3} (first node)))
                        (concat (flush-footnotes) [(walk-node node)])
                        [(walk-node node)])))))]

      (let [[tag & children] tree
            walked (walk children)
            final-footnotes (when (= tag :gemini)
                              (flush-footnotes))]
        (into [tag] (concat walked final-footnotes))))))

(defn render-with-footnotes [nodes]
  (-> nodes
      (walk-footnotes)
      (render-node)))

(defn render [node]
  "Just a prettier alias for external use."
  (render-node node))
