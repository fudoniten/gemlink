(ns gemlink.config
  "Configuration management for GemLink server.
   
   This namespace provides utilities for loading and validating server configuration.
   Configuration can come from multiple sources with the following precedence:
   
   1. Programmatic configuration (passed directly to functions)
   2. Environment variables (for deployment-specific settings)
   3. Configuration file (for shared/default settings)
   
   ## Configuration Options
   
   ### Server Configuration
   - `:port` - Port number to listen on (default: 1965)
   - `:host` - Host to bind to (default: \"0.0.0.0\")
   - `:max-concurrent-requests` - Maximum concurrent connections (default: 50)
   - `:shutdown-timeout-seconds` - Graceful shutdown timeout (default: 30)
   
   ### SSL Configuration
   - `:ssl/keystore-path` - Path to keystore file (required)
   - `:ssl/keystore-password` - Keystore password (required)
   - `:ssl/keystore-type` - Keystore format: \"PKCS12\", \"JKS\" (default: \"PKCS12\")
   - `:ssl/tls-protocol` - TLS version: \"TLS\", \"TLSv1.2\", \"TLSv1.3\" (default: \"TLS\")
   - `:ssl/key-manager-algorithm` - Key manager algorithm (default: \"SunX509\")
   
   ### Middleware Configuration
   - `:middleware/close-delay-ms` - Socket close delay in milliseconds (default: 50)
   
   ### Logging Configuration
   - `:log-level` - Timbre log level: :trace, :debug, :info, :warn, :error, :fatal (default: :info)
   
   ## Example Usage
   
   ```clojure
   ;; Load from environment variables
   (def config (load-config-from-env))
   
   ;; Load from EDN file
   (def config (load-config-from-file \"config.edn\"))
   
   ;; Merge multiple sources
   (def config (merge-config
                 (load-config-from-file \"config.edn\")
                 (load-config-from-env)
                 {:port 3000})) ; programmatic override
   
   ;; Validate configuration
   (validate-config! config)
   ```")

;; Default configuration values
(def default-config
  {:port 1965
   :host "0.0.0.0"
   :max-concurrent-requests 50
   :shutdown-timeout-seconds 30
   :ssl/keystore-type "PKCS12"
   :ssl/tls-protocol "TLS"
   :ssl/key-manager-algorithm "SunX509"
   :middleware/close-delay-ms 50
   :log-level :info})

;; Environment variable mappings
;; Maps environment variable names to config keys
(def env-mappings
  {"GEMLINK_PORT" :port
   "GEMLINK_HOST" :host
   "GEMLINK_MAX_CONCURRENT" :max-concurrent-requests
   "GEMLINK_SHUTDOWN_TIMEOUT" :shutdown-timeout-seconds
   "GEMLINK_KEYSTORE_PATH" :ssl/keystore-path
   "GEMLINK_KEYSTORE_PASSWORD" :ssl/keystore-password
   "GEMLINK_KEYSTORE_TYPE" :ssl/keystore-type
   "GEMLINK_TLS_PROTOCOL" :ssl/tls-protocol
   "GEMLINK_LOG_LEVEL" :log-level})

(defn load-config-from-env
  "Load configuration from environment variables.
   Returns a map with configuration values found in the environment."
  []
  (reduce
    (fn [config [env-var config-key]]
      (if-let [value (System/getenv env-var)]
        (assoc config config-key
          (cond
            ;; Parse numeric values
            (#{:port :max-concurrent-requests :shutdown-timeout-seconds :middleware/close-delay-ms} config-key)
            (Long/parseLong value)
            
            ;; Parse log level as keyword
            (= config-key :log-level)
            (keyword value)
            
            ;; Everything else as string
            :else value))
        config))
    {}
    env-mappings))

(defn load-config-from-file
  "Load configuration from an EDN file.
   Returns a map with configuration values from the file."
  [file-path]
  (try
    (-> file-path slurp read-string)
    (catch Exception e
      (throw (ex-info (format "Failed to load configuration file: %s" file-path)
                      {:file file-path
                       :error (.getMessage e)}
                      e)))))

(defn merge-config
  "Merge multiple configuration maps with later maps taking precedence.
   Also applies default values for any missing keys."
  [& configs]
  (apply merge default-config configs))

(defn validate-config!
  "Validate configuration and throw an exception if invalid.
   Returns the config if valid."
  [config]
  (when-not (:ssl/keystore-path config)
    (throw (ex-info "SSL keystore path is required"
                    {:config config})))
  (when-not (:ssl/keystore-password config)
    (throw (ex-info "SSL keystore password is required"
                    {:config config})))
  (when-not (number? (:port config))
    (throw (ex-info "Port must be a number"
                    {:port (:port config)})))
  (when (or (< (:port config) 1) (> (:port config) 65535))
    (throw (ex-info "Port must be between 1 and 65535"
                    {:port (:port config)})))
  config)

;; Convenience function to load configuration from all sources
(defn load-config
  "Load configuration from all sources in order of precedence:
   1. Environment variables (highest priority)
   2. Configuration file (if provided)
   3. Default values (lowest priority)
   
   Usage:
     (load-config)  ; env vars + defaults
     (load-config \"config.edn\")  ; env vars + file + defaults"
  ([]
   (merge-config (load-config-from-env)))
  ([config-file]
   (merge-config
     (load-config-from-file config-file)
     (load-config-from-env))))
