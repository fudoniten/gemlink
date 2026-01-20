(ns gemlink.handlers
  (:require [clojure.stacktrace :refer [print-stack-trace]]
            [taoensso.timbre :as log]

            [gemlink.utils :refer [generate-listing generate-listing mime-type]]
            [gemlink.response :refer [success not-found-error bad-request-error unknown-server-error]]
            [gemlink.path :refer [get-file-contents join-paths split-path build-path file-accessible?] :as path])
  (:import clojure.lang.ExceptionInfo))

(defn static-handler
  [body]
  (fn [_] (success body)))

(defn path-handler
  [path {:keys [listing? index-file mime-type-reader]}]
  (fn [{:keys [uri remaining-path]}]
    (if-not remaining-path
      (cond listing?   (success (generate-listing uri path)
                                :mime-type "text/gemini")
            index-file (let [index-file (join-paths path index-file)]
                         (if (file-accessible? index-file)
                           (success (get-file-contents index-file)
                                    :mime-type (mime-type index-file))
                           (not-found-error)))
            :else      (not-found-error))
      (try (let [full-filename (join-paths path (build-path remaining-path))
                 mime-type     (mime-type full-filename mime-type-reader)
                 file-contents (get-file-contents full-filename)]
             (log/debug (format "serving file %s with mime-type %s"
                                full-filename mime-type))
             (success file-contents :mime-type mime-type))
           (catch ExceptionInfo e
             (case (:type (ex-data e))
               :unauthorized-access
               (bad-request-error "unauthorized")

               :file-not-found
               (not-found-error)

               (do (log/error (format "unexpected error: %s"
                                      (.getMessage e)))
                   (log/debug (with-out-str (print-stack-trace e)))
                   (unknown-server-error))))))))

(defn users-handler
  [users-handler-mapper & _opts]
  (fn [{:keys [remaining-path] :as req}]
    (let [[user & remaining] (split-path remaining-path)]
      (if-let [user-handler (users-handler-mapper user)]
        (do (log/debug (format "serving request for user %s" user))
            (user-handler (assoc req :remaining-path
                                 (build-path remaining))))
        (not-found-error (format "user not found: %s" user))))))
