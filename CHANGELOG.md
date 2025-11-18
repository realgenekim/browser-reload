# Changelog

All notable changes to browser-reload will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- **BREAKING**: `wrap-reload-script` now automatically includes no-cache headers
  - No need to use separate `wrap-no-cache` middleware anymore
  - Prevents infinite reload loops after server restart by default
  - Simplifies setup: just use `wrap-reload-script` alone

### Removed
- `wrap-no-cache` middleware (functionality now integrated into `wrap-reload-script`)

### Migration Guide

**Before:**
```clojure
(def app-dev
  (-> #'app
      reload/wrap-no-cache           ;; No longer needed
      reload/wrap-reload-script
      (wrap-reload {:dirs ["src" "resources"]})))
```

**After:**
```clojure
(def app-dev
  (-> #'app
      reload/wrap-reload-script      ;; Now includes no-cache automatically
      (wrap-reload {:dirs ["src" "resources"]})))
```

## [0.1.0] - 2024-11-02

### Added
- Initial release: browser-reload library
- Browser auto-reload via file watching + HTTP polling
- Ring middleware (`wrap-reload-script`)
- File watcher integration (`start-file-watcher!`)
- Manual REPL trigger (`trigger-reload!`)
- Zero-config setup for Ring/Reitit apps
- `wrap-no-cache` middleware to prevent reload loops
