(ns gemlink.handlers
  (:require [clojure.stacktrace :refer [print-stack-trace]]

            [gemlink.logging :as log]
            [gemlink.utils :refer [generate-listing generate-listing mime-type]]
            [gemlink.response :refer [success not-found-error bad-request-error unknown-server-error]]
            [gemlink.path :refer [get-file-contents join-paths split-path build-path] :as path])
  (:import clojure.lang.ExceptionInfo))

(defn static-handler
  [body]
  (fn [_] (success body)))

(defn path-handler
  [path {:keys [listing? index-file mime-type-reader logger]}]
  (fn [{:keys [uri remaining-path]}]
    (if-not remaining-path
      (cond listing?   (success (generate-listing uri path)
                                :mime-type "text/gemini")
            index-file (let [index-file (join-paths path index-file)]
                         (success (get-file-contents index-file)
                                  :mime-type (mime-type index-file)))
            :else      (not-found-error))
      (try (let [full-filename (join-paths path remaining-path)
                 mime-type     (mime-type full-filename mime-type-reader)
                 file-contents (get-file-contents full-filename)]
             (success file-contents :mime-type mime-type))
           (catch ExceptionInfo e
             (case (:type (ex-data e))
               ::path/unauthorized-access
               (bad-request-error "unauthorized")

               ::path/file-not-found
               (not-found-error)

               (do (log/error! logger (format "unexpected error: %s"
                                              (.getMessage e)))
                   (log/debug! logger (with-out-str (print-stack-trace e)))
                   (unknown-server-error))))))))

(defn users-handler
  [users-path-mapper]
  (fn [{:keys [remaining-path] :as req}]
    (let [[user & remaining] (split-path remaining-path)]
      (if-let [user-handler (users-path-mapper user)]
        (user-handler (assoc req :remaining-path
                             (apply build-path remaining)))
        (not-found-error (format "user not found: %s" user))))))
