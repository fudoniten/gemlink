# Contributing to GemLink

Thank you for your interest in contributing to GemLink! This document provides guidelines and information for contributors.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Making Changes](#making-changes)
- [Code Style](#code-style)
- [Testing](#testing)
- [Submitting Changes](#submitting-changes)
- [Reporting Issues](#reporting-issues)

## Code of Conduct

Be respectful, constructive, and professional in all interactions. We aim to maintain a welcoming and inclusive community.

## Getting Started

1. Fork the repository on GitHub
2. Clone your fork locally
3. Create a new branch for your changes
4. Make your changes
5. Submit a pull request

## Development Setup

### Prerequisites

- Java 11 or later
- Clojure CLI tools (1.11 or later)
- Git
- (Optional) Nix for reproducible builds

### Using Clojure Tools

```bash
# Clone the repository
git clone https://github.com/fudoniten/gemlink.git
cd gemlink

# Run tests
clojure -X:test

# Start a REPL
clojure -M:repl
```

### Using Nix

```bash
# Enter development shell
nix develop

# Run tests
nix flake check

# Build package
nix build
```

## Making Changes

### Branch Naming

Use descriptive branch names:

- `feature/add-rate-limiting` - New features
- `fix/path-traversal-bug` - Bug fixes
- `docs/improve-readme` - Documentation updates
- `refactor/simplify-routing` - Code refactoring
- `test/add-handler-tests` - Test additions

### Commit Messages

Write clear, descriptive commit messages:

```
Brief summary of changes (50 chars or less)

More detailed explanation if needed. Wrap at 72 characters.
Explain what and why, not how.

- Bullet points are fine
- Reference issues: Fixes #123, Closes #456
```

Good examples:
```
Add rate limiting middleware

Implements status code 44 (slow down) response and tracks request
rates per IP address. Configurable threshold and time window.

Closes #42
```

```
Fix path traversal vulnerability in join-paths

The previous implementation compared resolved paths against the
unnormalized base path, which could allow traversal attacks.
Now properly normalizes both paths before comparison.
```

## Code Style

### Clojure Style Guidelines

Follow these Clojure conventions:

1. **Indentation**: Use 2 spaces (never tabs)
2. **Line Length**: Keep lines under 80 characters when practical
3. **Naming**:
   - Functions: `kebab-case`
   - Predicates: End with `?` (e.g., `valid-path?`)
   - Side effects: End with `!` (e.g., `log/info!`)
   - Conversion: Use `->` (e.g., `string->path`)
   - Constants: `UPPER_SNAKE_CASE`

4. **Docstrings**: Add docstrings to all public functions

```clojure
(defn parse-url
  "Parses the request line into a URI and adds it to the request.
   Validates URL length per Gemini spec (max 1024 bytes)."
  [& {:keys [logger]}]
  ...)
```

5. **Type Hints**: Use type hints for Java interop
```clojure
(defn get-file-contents
  [^String filename]
  ...)
```

6. **Prefer let over nested forms**:
```clojure
;; Good
(let [data (fetch-data)
      processed (process data)
      result (format processed)]
  result)

;; Avoid
(format (process (fetch-data)))
```

7. **Use meaningful names**: Avoid abbreviations unless standard
```clojure
;; Good
(defn extract-path [request] ...)

;; Avoid
(defn ext-pth [req] ...)
```

### Code Organization

- Keep functions small and focused
- One namespace per file
- Group related functions together
- Separate pure functions from side effects
- Use protocols for extensibility

### Security Considerations

When making changes, consider:

- **Input Validation**: Always validate user input
- **Path Traversal**: Use `join-paths` for file system access
- **Resource Limits**: Prevent unbounded resource usage
- **Error Messages**: Don't leak sensitive information
- **Dependencies**: Keep dependencies minimal and up-to-date

## Testing

### Running Tests

```bash
# Run all tests
clojure -X:test

# Run specific namespace
clojure -X:test :nss '[gemlink.path-test]'

# Run with Nix
nix flake check
```

### Writing Tests

1. **Test Coverage**: All new code should have tests
2. **Test Organization**: Mirror source structure in test directory
3. **Test Names**: Use descriptive names

```clojure
(ns gemlink.path-test
  (:require [clojure.test :refer [deftest is testing]]
            [gemlink.path :as path]))

(deftest join-paths-test
  (testing "joins valid paths"
    (is (= "/base/sub" (path/join-paths "/base" "sub"))))

  (testing "prevents directory traversal"
    (is (thrown? Exception
                 (path/join-paths "/base" "../etc/passwd")))))
```

4. **Test Types**:
   - **Unit tests**: Test individual functions
   - **Integration tests**: Test component interactions
   - **Property tests**: Use `test.check` for generative testing
   - **Security tests**: Test security-critical code paths

5. **Security Testing**: All security-critical code requires tests:
   - Path traversal protection
   - URL validation
   - Certificate handling
   - Input sanitization

### Test Requirements

All pull requests must:
- Include tests for new functionality
- Maintain or improve test coverage
- Pass all existing tests
- Not introduce flaky tests

## Submitting Changes

### Before Submitting

1. **Run tests**: Ensure all tests pass
   ```bash
   clojure -X:test
   ```

2. **Check formatting**: Code should follow style guidelines

3. **Update documentation**: Update README/docs if needed

4. **Add tests**: Include tests for new functionality

5. **Review your changes**: Self-review before submitting

### Pull Request Process

1. **Create PR**: Open a pull request against the main branch

2. **Describe changes**: Use the PR template (if provided) and include:
   - What changes were made
   - Why they were made
   - Any breaking changes
   - Related issues

3. **Example PR description**:
   ```markdown
   ## Summary
   Adds rate limiting middleware to prevent DoS attacks.

   ## Changes
   - Added `rate-limit` middleware function
   - Implemented per-IP request tracking
   - Added status code 44 (slow down) response
   - Updated documentation

   ## Testing
   - Added unit tests for rate limiter
   - Added integration tests
   - Tested with 1000 concurrent requests

   ## Breaking Changes
   None

   Closes #42
   ```

4. **Respond to feedback**: Address review comments promptly

5. **Keep updated**: Rebase on main if needed

### Pull Request Requirements

Pull requests must:
- Pass all tests
- Include appropriate tests
- Follow code style guidelines
- Update documentation as needed
- Have clear commit messages
- Be focused on a single concern

## Reporting Issues

### Bug Reports

When reporting bugs, please include:

1. **Description**: Clear description of the bug
2. **Steps to Reproduce**: Minimal steps to reproduce
3. **Expected Behavior**: What should happen
4. **Actual Behavior**: What actually happens
5. **Environment**:
   - Clojure version
   - Java version
   - OS and version
6. **Stack Trace**: Include error messages/stack traces

### Feature Requests

When suggesting features:

1. **Use Case**: Describe the problem you're trying to solve
2. **Proposed Solution**: Suggest how it might work
3. **Alternatives**: Consider alternative approaches
4. **Compatibility**: Consider impact on existing code

### Security Issues

**Do not open public issues for security vulnerabilities.**

For security issues:
1. Open a GitHub security advisory
2. Or email the maintainers directly
3. Provide details privately
4. Allow time for a fix before public disclosure

## Development Workflow

### Typical Development Cycle

1. **Start REPL**: `clojure -M:repl`
2. **Load namespace**: `(require '[gemlink.core :as core] :reload)`
3. **Run tests**: `(require '[clojure.test]) (clojure.test/run-tests 'gemlink.core-test)`
4. **Iterate**: Make changes, reload, test
5. **Commit**: When satisfied, commit changes

### Debugging Tips

1. **Use println**: Simple but effective
2. **Use REPL**: Evaluate forms interactively
3. **Check logs**: Enable debug logging
4. **Add tests**: Write failing test first
5. **Use debugger**: Tools like CIDER debugger

## Project Architecture

Understanding the architecture helps with contributions:

### Core Components

- **core.clj**: Server startup, routing engine
- **middleware.clj**: Request/response pipeline
- **gemtext.clj**: DSL for generating Gemtext
- **handlers.clj**: Built-in request handlers
- **response.clj**: Response protocol and constructors
- **path.clj**: File system operations (security-critical)
- **utils.clj**: Utility functions
- **logging.clj**: Logging protocol

### Key Patterns

1. **Middleware**: Ring-inspired composable functions
2. **Protocols**: For extensibility (Response, Logger)
3. **Multimethods**: For dispatch (render-node)
4. **Immutability**: Pure functions, immutable data

## Resources

### Gemini Protocol
- [Specification](https://gemini.circumlunar.space/docs/specification.html)
- [Project Gemini](https://gemini.circumlunar.space/)

### Clojure
- [Clojure Style Guide](https://guide.clojure.style/)
- [Clojure Documentation](https://clojure.org/)
- [ClojureDocs](https://clojuredocs.org/)

### Testing
- [clojure.test](https://clojure.github.io/clojure/clojure.test-api.html)
- [test.check](https://github.com/clojure/test.check)

## Questions?

If you have questions:
- Open a GitHub discussion
- Ask in pull request comments
- Check existing issues and PRs

## License

By contributing, you agree that your contributions will be licensed under the BSD 3-Clause License.

## Acknowledgments

Thank you for contributing to GemLink! Your efforts help make this project better for everyone.
