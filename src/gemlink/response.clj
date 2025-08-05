(ns gemlink.response)

(defprotocol Response
  (get-status  [_])
  (get-header  [_])
  (get-body    [_])
  (is-error?   [_])
  (is-success? [_])
  (get-type    [_]))

(defn success
  "Creates a successful response with the given body and optional MIME type."
  [^String body & {:keys [mime-type]
                   :or   {mime-type "text/gemini"}}]
  (reify Response
    (get-status  [_] 20)
    (get-header  [_] mime-type)
    (get-body    [_] body)
    (is-error?   [_] false)
    (is-success? [_] true)
    (get-type    [_] :success)))

(defn bad-request-error
  "Creates a response indicating a bad request with the given message."
  [^String message]
  (reify Response
    (get-status  [_] 59)
    (get-header  [_] "bad request")
    (get-body    [_] message)
    (is-error?   [_] true)
    (is-success? [_] false)
    (get-type    [_] :bad-request-error)))

(defn unknown-server-error
  "Creates a response indicating an unknown server error with the given message."
  ([] (unknown-server-error "an unknown error occurred"))
  ([^String message]
   (reify Response
     (get-status  [_] 40)
     (get-header  [_] "unknown error")
     (get-body    [_] message)
     (is-error?   [_] true)
     (is-success? [_] false)
     (get-type    [_] :unknown-server-error))))

(defn not-found-error
  "Creates a response indicating that the requested resource was not found."
  ([] (not-found-error "resource not found"))
  ([^String message]
   (reify Response
     (get-status  [_] 51)
     (get-header  [_] "not found")
     (get-body    [_] message)
     (is-error?   [_] true)
     (is-success? [_] false)
     (get-type    [_] :not-found-error))))

(defn not-authorized-error
  "Creates a response indicating that the user was not authorized to access the requested resource."
  [^String message]
  (reify Response
    (get-status  [_] 61)
    (get-header  [_] "certificate not authorized")
    (get-body    [_] message)
    (is-error?   [_] true)
    (is-success? [_] false)
    (get-type    [_] :not-authorized-error)))

(defn response? [o] (satisfies? Response o))

(defn temporary-redirect
  "Creates a response redirecting the requester to a different URI."
  [^String uri]
  (reify Response
    (get-status  [_] 30)
    (get-header  [_] uri)
    (get-body    [_] nil)
    (is-error?   [_] false)
    (is-success? [_] true)
    (get-type    [_] :temporary-redirect)))

(defn permanent-redirect
  "Creates a response redirecting the requester to a different URI."
  [^String uri]
  (reify Response
    (get-status  [_] 31)
    (get-header  [_] uri)
    (get-body    [_] nil)
    (is-error?   [_] false)
    (is-success? [_] true)
    (get-type    [_] :permanent-redirect)))
