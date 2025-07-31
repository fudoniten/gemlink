(ns gemlink.response)

(defprotocol Response
  (get-status [_])
  (get-header [_])
  (get-body   [_]))

(defn success
  "Creates a successful response with the given body and optional MIME type."
  [^String body & {:keys [mime-type]
                   :or   {mime-type "text/gemini"}}]
  (reify Response
    (get-status [_] 20)
    (get-header [_] mime-type)
    (get-body   [_] body)))

(defn bad-request-error
  "Creates a response indicating a bad request with the given message."
  [^String message]
  (reify Response
    (get-status [_] 59)
    (get-header [_] "bad request")
    (get-body   [_] message)))

(defn unknown-server-error
  "Creates a response indicating an unknown server error with the given message."
  [^String message]
  (reify Response
    (get-status [_] 40)
    (get-header [_] "unknown error")
    (get-body   [_] message)))

(defn not-found-error
  "Creates a response indicating that the requested resource was not found."
  [^String message]
  (reify Response
    (get-status [_] 51)
    (get-header [_] "not found")
    (get-body   [_] message)))

(defn response? 
  "Checks if the given object is a Response."
  [o] 
  (satisfies? Response o))
