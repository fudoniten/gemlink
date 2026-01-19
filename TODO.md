# GemLink TODO List

This document tracks improvements, missing features, and enhancements for the GemLink Gemini server.

## Legend
- [ ] Not started
- [x] Completed

---

## High Priority

### Missing Gemini Protocol Features
- [ ] Implement status code 10/11 (input requests - sensitive/normal)
  - Core feature for forms and user input
  - Add query string parsing support
  - Create response constructors for input requests
  - File: `src/gemlink/response.clj`

- [ ] Implement status code 42 (CGI error)
  - File: `src/gemlink/response.clj`

- [ ] Implement status code 44 (slow down / rate limiting)
  - Add rate limiting middleware
  - Track request rates per IP
  - File: `src/gemlink/middleware.clj`

- [ ] Implement status codes 60-62 (client certificate authentication)
  - [x] Extract client certificates from SSL session (completed)
  - [ ] Add certificate validation helpers
  - [ ] Add certificate-required response (60)
  - [ ] Add certificate-not-authorized response (61)
  - [ ] Add certificate-not-valid response (62)
  - Files: `src/gemlink/response.clj`, `src/gemlink/middleware.clj`

### Test Coverage Gaps

- [ ] Create test file for `handlers.clj`
  - [ ] Test static-handler
  - [ ] Test path-handler with file serving
  - [ ] Test path-handler with directory listing
  - [ ] Test users-handler
  - File: `test/gemlink/handlers_test.clj`

- [ ] Create test file for `logging.clj`
  - [ ] Test logger protocol
  - [ ] Test print-logger implementation
  - [ ] Test log level filtering
  - File: `test/gemlink/logging_test.clj`

- [ ] Create test file for `path.clj` (**CRITICAL - security implications**)
  - [ ] Test path traversal protection in `join-paths`
  - [ ] Test file access checks
  - [ ] Test directory listing
  - [ ] Fuzz testing for path manipulation
  - File: `test/gemlink/path_test.clj`

- [ ] Add integration tests
  - [ ] End-to-end server tests
  - [ ] Full request/response cycle tests
  - [ ] SSL/TLS connection tests
  - [ ] Client certificate authentication flow
  - File: `test/gemlink/integration_test.clj`

- [ ] Add property-based tests using test.check
  - [ ] Route matching properties
  - [ ] Gemtext rendering properties
  - [ ] Path manipulation properties

- [ ] Add performance/load tests
  - [ ] Benchmark routing performance
  - [ ] Benchmark Gemtext rendering
  - [ ] Load testing with concurrent requests
  - [ ] Thread pool behavior under load

### Documentation

- [ ] Create LICENSE file
  - README mentions MIT License but file is missing
  - Add proper MIT License text

- [ ] Expand README.md
  - [ ] Add installation instructions
  - [ ] Add quick start guide
  - [ ] Add usage examples (basic server setup)
  - [ ] Document configuration options
  - [ ] Add SSL certificate setup guide
  - [ ] Add deployment guide
  - [ ] Add API documentation overview

- [ ] Create CONTRIBUTING.md
  - [ ] Add contribution guidelines
  - [ ] Code style guide
  - [ ] Testing requirements
  - [ ] Pull request process

- [ ] Create CHANGELOG.md
  - [ ] Document version history
  - [ ] Track breaking changes
  - [ ] Follow semantic versioning

### Infrastructure

- [ ] Add CI/CD pipeline (GitHub Actions)
  - [ ] Run tests on all PRs
  - [ ] Run tests on multiple OS (Linux, macOS)
  - [ ] Check code formatting
  - [ ] Run linter (clj-kondo)
  - [ ] Build and publish releases
  - File: `.github/workflows/ci.yml`

- [ ] Fix Nix configuration issues
  - [ ] Fix nixpkgs version (currently `nixos-25.05` which doesn't exist)
  - Should be `nixos-24.05` or `nixos-unstable`
  - File: `flake.nix:5`

---

## Medium Priority

### Code Quality

- [ ] Remove debug code
  - [ ] Remove or relocate `pthru` function in `utils.clj:91`
  - Move to dev namespace or delete if unused

- [ ] Fix duplicate function reference
  - [ ] Fix `generate-listing` listed twice in require
  - File: `src/gemlink/handlers.clj:5`

- [ ] Document magic numbers
  - [ ] Document or make configurable the 50ms sleep in middleware
  - File: `src/gemlink/middleware.clj:56`

- [ ] Fix potential infinite loop
  - [ ] Review `:seq` multimethod dispatch in `render-node`
  - Ensure `normalize-node` doesn't return sequential non-vectors
  - File: `src/gemlink/gemtext.clj:83-85`

- [ ] Improve error handling consistency
  - [ ] Standardize on exception throwing vs error responses
  - [ ] Document error handling patterns
  - Create error handling guide

### Architecture Improvements

- [ ] Implement graceful shutdown
  - [ ] Track active connections in `serve-requests`
  - [ ] Wait for in-flight requests on shutdown
  - [ ] Add configurable shutdown timeout
  - File: `src/gemlink/core.clj`

- [ ] Make SSL configuration more flexible
  - [ ] Support additional keystore formats (JKS, etc.)
  - [ ] Allow TLS protocol version configuration
  - [ ] Add custom trust manager support
  - [ ] Add cipher suite configuration
  - File: `src/gemlink/core.clj:28-37`

- [ ] Add configuration management
  - [ ] Add schema validation (consider malli or spec)
  - [ ] Add default configuration values
  - [ ] Add environment variable support
  - [ ] Create example configuration file
  - Consider libraries: aero, cprop

- [ ] Refactor global mutable state
  - [ ] Make `REGISTERED_EXTENSIONS` injectable
  - [ ] Improve testability
  - File: `src/gemlink/utils.clj:79-83`

### Observability

- [ ] Add structured logging
  - [ ] Replace println with proper logging
  - [ ] Add request IDs for tracing
  - [ ] Add log levels throughout
  - Consider libraries: timbre, mulog

- [ ] Add metrics/instrumentation
  - [ ] Track request counts
  - [ ] Track request latency
  - [ ] Track error rates
  - [ ] Track concurrent connections

- [ ] Add request tracing
  - [ ] Generate unique request IDs
  - [ ] Propagate request context
  - [ ] Log request lifecycle events

### Security

- [ ] Add dependency vulnerability scanning
  - [ ] Integrate OWASP dependency check
  - [ ] Regular security audits
  - [ ] Automated vulnerability alerts

- [ ] Add security testing
  - [ ] Fuzzing tests for path traversal
  - [ ] Fuzzing tests for URL parsing
  - [ ] Test certificate validation edge cases
  - [ ] Test DoS scenarios

### Gemtext DSL Enhancements

- [ ] Add alt text support for preformatted blocks
  - Support ```alt text syntax
  - File: `src/gemlink/gemtext.clj`

- [ ] Add link validation
  - [ ] Validate URL format in links
  - [ ] Warn on malformed links

- [ ] Improve footnote system
  - [ ] Add custom footnote numbering
  - [ ] Allow multiple references to same footnote
  - [ ] Make footnote flushing configurable
  - File: `src/gemlink/gemtext.clj`

---

## Low Priority

### Developer Experience

- [ ] Enhance Nix development shell
  - [ ] Add REPL tools to dev shell
  - [ ] Add clj-kondo linter
  - [ ] Add cljfmt formatter
  - [ ] Add editor integration tools
  - File: `flake.nix`

- [ ] Add Docker support
  - [ ] Create Dockerfile
  - [ ] Create docker-compose for development
  - [ ] Add container build to CI/CD
  - [ ] Document container deployment

- [ ] Create example applications
  - [ ] Simple static site server
  - [ ] Blog server example
  - [ ] Dynamic content example with input
  - [ ] User authentication example with certificates
  - Directory: `examples/`

- [ ] Add development tooling
  - [ ] Add pre-commit hooks
  - [ ] Add commit message linting
  - [ ] Add automatic code formatting
  - [ ] Add documentation generation

### Documentation

- [ ] Add API documentation
  - [ ] Document all public functions with docstrings
  - [ ] Add usage examples in docstrings
  - [ ] Generate API docs (codox)

- [ ] Create user guide
  - [ ] Routing guide
  - [ ] Middleware composition guide
  - [ ] Gemtext DSL tutorial
  - [ ] Authentication with certificates guide
  - [ ] Static file serving guide

- [ ] Add architecture documentation
  - [ ] System design overview
  - [ ] Data flow diagrams
  - [ ] Security model documentation
  - [ ] Extension points documentation

### Future Enhancements

- [ ] Add caching support
  - [ ] Static file caching
  - [ ] Response caching middleware
  - [ ] Cache headers/TTL support

- [ ] Add WebSocket-like support
  - [ ] Investigate Gemini protocol extensions
  - [ ] Long-running connections

- [ ] Add administration interface
  - [ ] Server statistics endpoint
  - [ ] Health check endpoint
  - [ ] Configuration reload

- [ ] Add plugin/extension system
  - [ ] Define extension points
  - [ ] Plugin loading mechanism
  - [ ] Example plugins

- [ ] Performance optimizations
  - [ ] Benchmark and profile
  - [ ] Optimize hot paths
  - [ ] Reduce memory allocations
  - [ ] Consider ahead-of-time compilation (GraalVM native-image)

---

## Completed ✓

- [x] Fix path traversal security bug in `join-paths` (src/gemlink/path.clj:33)
- [x] Fix exception not thrown in `get-file-contents` (src/gemlink/path.clj:43-45)
- [x] Add URL length validation (1024 byte limit per Gemini spec)
- [x] Add client certificate extraction from SSL session
- [x] Fix unbounded thread creation with bounded ExecutorService

---

## Notes

### Dependency Considerations

While the minimal dependency approach is commendable, consider adding:

- **Schema validation**: `malli` or `clojure.spec` for configuration and data validation
- **Configuration**: `aero` or `cprop` for better configuration management
- **Structured logging**: `timbre` or `mulog` for production-ready logging
- **Testing**: Already has `test.check`, but could use `kaocha` for better test runner

### Breaking Changes to Consider

Some improvements may require breaking changes:

- Configuration format changes (if adding schema validation)
- Response format changes (if improving error handling)
- Middleware signature changes (if adding request context/tracing)

Plan these carefully and version appropriately.

### Performance Targets

Before optimizing, establish baselines:

- Requests per second under load
- Memory usage patterns
- Latency percentiles (p50, p95, p99)
- Thread pool saturation points

### Security Audit Checklist

- [ ] Path traversal protections tested
- [ ] URL parsing edge cases covered
- [ ] Certificate validation secure
- [ ] No information leakage in error messages
- [ ] DoS protections in place
- [ ] Input validation throughout
- [ ] Dependencies free of known vulnerabilities
