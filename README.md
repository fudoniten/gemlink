# GemLink - A Gemini Protocol Server

[![License](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](LICENSE)

GemLink is a lightweight, elegant Gemini protocol server written in Clojure. The Gemini protocol is a modern, privacy-focused alternative to HTTP designed for serving content with minimal complexity.

## Features

- **Ring-Inspired Architecture**: Composable middleware pattern for clean, modular code
- **Hiccup-Like DSL**: Intuitive Gemtext generation with a familiar syntax
- **Secure by Default**: TLS/SSL required, with client certificate support
- **Flexible Routing**: Path-based routing with parameter support
- **Static File Serving**: Built-in handlers for serving files and directories
- **Resource Management**: Bounded thread pool prevents resource exhaustion
- **Security Hardened**: Path traversal protection, URL validation, and more

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Routing](#routing)
- [Gemtext DSL](#gemtext-dsl)
- [Middleware](#middleware)
- [Handlers](#handlers)
- [SSL/TLS Setup](#ssltls-setup)
- [Testing](#testing)
- [Contributing](#contributing)
- [License](#license)

## Installation

### Prerequisites

- Java 11 or later
- Clojure CLI tools (1.11 or later)
- (Optional) Nix for reproducible builds

### Using deps.edn

Add GemLink to your `deps.edn`:

```clojure
{:deps {org.fudo/gemlink {:git/url "https://github.com/fudoniten/gemlink"
                          :git/sha "..."}}}
```

### Using Nix

```bash
nix build
nix run
```

## Quick Start

Here's a minimal Gemini server:

```clojure
(ns my-server
  (:require [gemlink.core :as gemlink]
            [gemlink.response :as response]
            [gemlink.gemtext :as gemtext]
            [gemlink.logging :as log]))

(defn handler [request]
  (response/success
    (gemtext/render
      [:gemini
       [:h1 "Welcome to My Gemini Server!"]
       [:text "This is a simple Gemini page."]
       [:link "gemini://example.com/about" "About Us"]
       [:list ["Item 1" "Item 2" "Item 3"]]])))

(defn -main [& args]
  (let [logger (log/print-logger)
        ssl-context (gemlink/load-ssl-context "keystore.p12" "password")
        ctx {:logger logger
             :ssl-context ssl-context
             :port 1965}]
    (gemlink/start-server ctx handler)
    (println "Server started on port 1965")))
```

Run with:

```bash
clojure -M -m my-server
```

## Configuration

### Server Options

When starting a server with `start-server`, you can configure:

```clojure
{:logger logger                    ; Logger instance (required)
 :ssl-context ssl-context          ; SSL context (required)
 :port 1965                        ; Port number (default: 1965)
 :max-concurrent-requests 50}      ; Max concurrent connections (default: 50)
```

### SSL Context

Create an SSL context from a PKCS12 keystore:

```clojure
(gemlink/load-ssl-context "path/to/keystore.p12" "keystore-password")
```

## Routing

GemLink provides flexible routing with support for path parameters:

```clojure
(ns my-server.routes
  (:require [gemlink.core :refer [define-routes]]
            [gemlink.handlers :as handlers]
            [gemlink.response :as response]))

(def routes
  {:logger logger
   :children
   {"" {:handler (fn [req] (response/success "Home page"))}

    "about" {:handler (fn [req] (response/success "About page"))}

    "blog" {:children
            {"" {:handler (fn [req] (response/success "Blog listing"))}
             ":post-id" {:handler (fn [req]
                                    (let [post-id (get-in req [:params :post-id])]
                                      (response/success (str "Post: " post-id))))}}}

    "static" {:handler (handlers/path-handler "/var/www/gemini")}}})

(def app-handler (define-routes routes))
```

### Route Structure

Routes are defined as nested maps:

- `:handler` - Function that handles requests at this path
- `:children` - Map of sub-paths
- `:middleware` - Vector of middleware functions
- `:logger` - Logger instance

### Path Parameters

Prefix a route segment with `:` to capture it as a parameter:

```clojure
"users" {:children
         {":username" {:handler (fn [req]
                                  (let [username (get-in req [:params :username])]
                                    (response/success (str "User: " username))))}}}
```

## Gemtext DSL

GemLink provides a Hiccup-inspired DSL for generating Gemtext:

```clojure
(require '[gemlink.gemtext :as gemtext])

(gemtext/render
  [:gemini
   ;; Headers (h1, h2, h3)
   [:h1 "Main Title"]
   [:h2 "Subtitle"]
   [:h3 "Section"]

   ;; Plain text
   [:text "This is a paragraph of text."]
   [:text "Another paragraph."]

   ;; Links
   [:link "gemini://example.com" "External Link"]
   [:link "/local/path" "Local Link"]

   ;; Lists
   [:list ["First item"
           "Second item"
           "Third item"]]

   ;; Quotes
   [:quote "This is a quoted block of text."]

   ;; Preformatted text
   [:pre "def hello():\n    print('Hello, world!')"]

   ;; Block (multiple lines at once)
   [:block "Line 1\nLine 2\nLine 3"]])
```

### Footnotes

GemLink supports automatic footnote generation:

```clojure
(gemtext/render-with-footnotes
  [:gemini
   [:text "Some text with a reference." :footnote "This is the footnote."]
   [:text "More text with another reference." :footnote "Another footnote."]])
```

Footnotes are automatically numbered and rendered at appropriate points.

## Middleware

GemLink uses a Ring-inspired middleware pattern:

### Built-in Middleware

```clojure
(require '[gemlink.middleware :as mw])

;; Parse URL from request line
(mw/parse-url :logger logger)

;; Extract path components for routing
(mw/extract-path :logger logger)

;; Log requests
(mw/log-requests :logger logger)

;; Log responses
(mw/log-responses :logger logger)

;; Ensure errors return proper responses
(mw/ensure-return :logger logger)
```

### Composing Middleware

```clojure
(require '[gemlink.core :refer [fold-middleware]])

(def my-middleware
  (fold-middleware
    (mw/parse-url :logger logger)
    (mw/extract-path :logger logger)
    (mw/log-requests :logger logger)
    (mw/ensure-return :logger logger)))

(def wrapped-handler (my-middleware base-handler))
```

### Custom Middleware

Middleware functions take a handler and return a new handler:

```clojure
(defn timing-middleware [& {:keys [logger]}]
  (fn [handler]
    (fn [request]
      (let [start (System/currentTimeMillis)
            response (handler request)
            duration (- (System/currentTimeMillis) start)]
        (log/info! logger (format "Request took %dms" duration))
        response))))
```

### Request Map

The request map contains:

```clojure
{:request-line "gemini://example.com/path"  ; Raw request line
 :remote-addr #<InetAddress>                ; Client IP address
 :remote-port 12345                         ; Client port
 :local-port 1965                           ; Server port
 :tls-protocol "TLSv1.3"                    ; TLS protocol version
 :tls-cipher "TLS_AES_256_GCM_SHA384"       ; Cipher suite
 :client-certs [...]                        ; Client certificates (if any)
 :uri #<URI>                                ; Parsed URI
 :full-path "/path"                         ; Full request path
 :remaining-path ["path"]                   ; Path segments for routing
 :params {:key "value"}}                    ; Extracted route parameters
```

## Handlers

### Static Content Handler

Serve static text:

```clojure
(require '[gemlink.handlers :as handlers])

(handlers/static-handler "This is static content")
```

### File/Directory Handler

Serve files from the filesystem with directory listing:

```clojure
(handlers/path-handler "/path/to/gemini/content")
```

Features:
- Automatic MIME type detection
- Directory listings
- Path traversal protection
- Serves `.gmi` files as `text/gemini`

### User Handler

Dynamic routing based on path:

```clojure
(handlers/users-handler)
```

## SSL/TLS Setup

### Creating a Self-Signed Certificate

For development:

```bash
# Generate a self-signed certificate
openssl req -x509 -newkey rsa:4096 -keyout key.pem -out cert.pem -days 365 -nodes

# Convert to PKCS12 format
openssl pkcs12 -export -out keystore.p12 -inkey key.pem -in cert.pem -password pass:changeit
```

### Using Let's Encrypt

For production with Let's Encrypt certificates:

```bash
# After obtaining certificates with certbot
openssl pkcs12 -export \
  -out keystore.p12 \
  -inkey /etc/letsencrypt/live/example.com/privkey.pem \
  -in /etc/letsencrypt/live/example.com/fullchain.pem \
  -password pass:your-password
```

### Client Certificates

GemLink automatically extracts client certificates. Access them in your handler:

```clojure
(defn auth-handler [request]
  (if-let [certs (:client-certs request)]
    (response/success "Authenticated!")
    (response/client-cert-required "Authentication required")))
```

## Response Types

```clojure
(require '[gemlink.response :as response])

;; Success responses
(response/success "content")                    ; 20 text/gemini
(response/success "content" "text/plain")       ; 20 with custom MIME type

;; Redirects
(response/redirect "gemini://new-location")     ; 30 temporary redirect
(response/permanent-redirect "gemini://new")    ; 31 permanent redirect

;; Client errors
(response/bad-request-error "Invalid request")  ; 40 bad request
(response/not-found-error "Not found")          ; 51 not found
(response/unauthorized-error "Forbidden")       ; 61 unauthorized

;; Server errors
(response/unknown-server-error "Error")         ; 59 server error
```

## Testing

Run the test suite:

```bash
# Run all tests
clojure -X:test

# Run specific test namespace
clojure -X:test :nss '[gemlink.core-test]'

# Using Nix
nix flake check
```

## Project Structure

```
gemlink/
├── src/gemlink/
│   ├── core.clj          # Server and routing
│   ├── gemtext.clj       # Gemtext DSL
│   ├── handlers.clj      # Built-in handlers
│   ├── middleware.clj    # Middleware functions
│   ├── response.clj      # Response types
│   ├── path.clj          # Path utilities
│   ├── utils.clj         # General utilities
│   └── logging.clj       # Logging protocol
├── test/gemlink/         # Test suite
├── deps.edn              # Dependencies
├── flake.nix             # Nix build configuration
└── README.md             # This file
```

## Inspiration

This project draws inspiration from:

- **Ring**: The Clojure web library that pioneered the middleware pattern
- **Hiccup**: The elegant HTML DSL that inspired our Gemtext DSL
- **Gemini Protocol**: A lightweight, privacy-focused alternative to HTTP

## Security

GemLink includes several security features:

- **TLS Required**: All connections must use TLS/SSL
- **Path Traversal Protection**: Prevents directory traversal attacks
- **URL Length Validation**: Enforces Gemini spec's 1024 byte limit
- **Bounded Concurrency**: Prevents resource exhaustion attacks
- **Client Certificate Support**: For authentication and authorization

Found a security issue? Please report it responsibly by opening a GitHub issue.

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Resources

- [Gemini Protocol Specification](https://gemini.circumlunar.space/docs/specification.html)
- [Project Gemini](https://gemini.circumlunar.space/)
- [Awesome Gemini](https://github.com/kr1sp1n/awesome-gemini)

## License

This project is licensed under the BSD 3-Clause License. See the [LICENSE](LICENSE) file for details.

## Acknowledgments

Thanks to the Gemini community for creating a refreshing alternative to the complexity of modern web protocols.
