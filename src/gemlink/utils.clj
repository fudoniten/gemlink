(ns gemlink.utils
  (:require [clojure.pprint :refer [pprint]]
            [gemlink.path :refer [file-accessible? file-extension list-directory join-paths split-path build-path]]
            [gemlink.gemtext :refer [render-node]])
  (:import [java.net URI]
           [java.nio.file Paths Files]
           [java.nio.file.attribute FileAttribute]))

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

(defn parse-route-config
  "Parses a route configuration into a nested map structure.
   Accepts either a single route path + config, or a list of such routes."
  [route-cfg]
  (letfn [(terminate! []
            (throw (ex-info "route fails to terminate; every path must end in a :handler" {})))

          (path-parser [[path & subroute-cfg]]
            (when-not (string? path)
              (throw (ex-info (format "route path must be a string, got: %s" path) {})))

            (let [[this & remaining] (split-path path)]
              (if remaining
                ;; Nested path: build a new path from the rest and recurse
                {this {:children (path-parser (cons (build-path remaining) subroute-cfg))}}

                ;; Terminal path
                (let [[maybe-cfg & maybe-subroutes] subroute-cfg
                      [cfg subroutes] (if (map? maybe-cfg)
                                        [maybe-cfg maybe-subroutes]
                                        [{} subroute-cfg])]

                  (cond
                    (seq subroutes)
                    {this (assoc cfg :children
                                 (parse-route-config subroutes))}

                    (:handler cfg)
                    {this cfg}

                    :else (terminate!))))))

          (path-dispatcher [args]
            (if (string? (first args))
              (apply path-parser args)
              ;; multiple path/config pairs
              (into {} (map path-parser) args)))]

    (when-not (seq route-cfg)
      (terminate!))

    (path-dispatcher route-cfg)))

(def REGISTERED_EXTENSIONS
  "A map of known extensions to mime type."
  (atom {:gmi    "text/gemini"
         :gemini "text/gemini"
         :gem    "text/gemini"}))

(defn register-extension
  "Add a new extension to the map of extension to mime type."
  [ext mime-type]
  (swap! REGISTERED_EXTENSIONS
         update ext mime-type))

(defn pthru [o] (println o) o)

(defn mime-type
  "Given a filename, return the best guess at a mime-type for the file."
  ([^String filename] (mime-type filename nil))
  ([^String filename mime-type-reader]
   (when-not (file-accessible? filename)
     (throw (ex-info (format "missing file: %s" filename)
                     {:type :file-not-found})))
   (if-let [mime-type (some->> (file-extension filename)
                                 (get @REGISTERED_EXTENSIONS))]
     mime-type
     (if mime-type-reader
       (mime-type-reader filename)
       (let [p (Paths/get filename (make-array String 0))]
         (Files/probeContentType p))))))

(defn make-temp-directory
  [prefix]
  (Files/createTempDirectory prefix (into-array FileAttribute [])))

(defn create-file
  [filename]
  (Files/createFile filename (into-array FileAttribute [])))

(defn create-directory
  [dirname]
  (Files/createDirectory dirname (into-array FileAttribute [])))

(defn alter-uri
  [uri & {:keys [path]}]
  (URI. (.getScheme uri)
        (.getUserInfo uri)
        (.getHost uri)
        (.getPort uri)
        (or path (.getPath uri))
        (.getQuery uri)
        (.getFragment uri)))

(defn generate-listing
  [base-uri path]
  (letfn [(render-file [file]
            [:link
             :text (.getName file)
             :uri  (str (alter-uri base-uri :path (join-paths (.getPath base-uri) (.getName file))))])]
    (let [contents (list-directory path)]
      (render-node (concat [:gemini
                            [:h1 (.getPath base-uri)]
                            [:text (str "contents of " (.getPath base-uri))]]
                           (map render-file contents))))))
