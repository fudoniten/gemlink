(ns gemlink.path
  (:require [clojure.string :as str])
  (:import [java.nio.file Paths]))

(defn file-accessible?
  "Check if a file exists, and is a readable file."
  [filename]
  (let [f (java.io.File. filename)]
    (and (.exists f)
         (.isFile f)
         (.canRead f))))

(defn file-extension
  "Grab the extension (if any) of a file, as a keyword."
  [^String path]
  (let [name (.getName (java.io.File. path))
        idx  (.lastIndexOf name ".")]
    (when (pos? idx)
      (-> (subs name (inc idx))
          (keyword)))))

(defn join-paths
  "Given a base path and subpath, return a full path. Throw an exception if the resulting path is outside of the base path."
  [^String base ^String sub]
  (let [sanitize-subpath (fn [^String subpath]
                           (let [p (str/replace-first subpath "^/" "")]
                             (if (str/blank? p)
                               "."
                               p)))
        base-path (Paths/get base
                             (into-array String [(sanitize-subpath sub)]))
        resolved  (.normalize base-path)]
    (if (.startsWith resolved base-path)
      (str resolved)
      (throw (ex-info "path is not within base path!"
                      {:resolved (str resolved)
                       :base     (str base-path)
                       :type     :unauthorized-access})))))

(defn get-file-contents
  "Slurp the content of a file, throwing an exception if the file doesn't exist."
  [^String filename]
  (when-not (file-accessible? filename)
    (ex-info (format "missing file: %s" filename)
             {:type :file-not-found}))
  (slurp filename))

(defn list-directory
  [^String dir]
  (-> (java.io.File. dir)
      (.listFiles)
      (vec)
      (doall)))

(defn string->path
  [^String path]
  (Paths/get path (into-array String [])))

(defn split-path
  "Splits a path string into a vector of non-empty segments."
  [path]
  (assert (or (sequential? path) (string? path))
          (format "split-path expects a `sequential?` or a `string?`, got `%s`"
                  path))
  (if (sequential? path)
    path
    (->> (str/split path #"/")
         (remove empty?)
         vec)))

(defn build-path
  "Builds a path from path elements."
  [els]
  (str "/" (str/join "/" (map name els))))
