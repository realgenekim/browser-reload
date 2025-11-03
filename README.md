# browser-reload

> Sublime browser auto-reload for Clojure web development. Edit → Save → Browser refreshes automatically.

Zero-friction development experience combining server-side code reloading (Ring's `wrap-reload`) with automatic browser refresh on file changes.

## What You Get

- **Edit any file → Browser auto-refreshes** (CSS, JavaScript, Clojure views)
- **No manual reload needed** - Just save and see your changes instantly
- **Server-side code reloading** - Handler changes reload on each request
- **File watching** - Automatically detects changes to configured paths/extensions
- **Development mode only** - Zero overhead in production
- **REPL-friendly** - Manual trigger available for advanced workflows

## Quick Start

### 1. Add Dependency

Add to your `deps.edn`:

```clojure
{:deps {; ... your other deps
        }
 :aliases {:web {:extra-deps {ring/ring-devel {:mvn/version "1.12.2"}
                              hawk/hawk {:mvn/version "0.2.11"}
                              browser-reload/browser-reload {:local/root "../browser-reload"}}
                 :jvm-opts ["-DENV=dev"]}}}
```

### 2. Add Route & Middleware (4 lines)

```clojure
(ns my-app.server
  (:require [browser-reload.core :as reload]
            [reitit.ring :as reitit]
            [ring.middleware.reload :refer [wrap-reload]]))

(def routes
  [["/" {:get home-handler}]
   ;; Add reload endpoint (1 line)
   ["/dev/reload-check" {:get reload/reload-check-handler}]])

(def app
  (-> (reitit/ring-handler (reitit/router routes))
      reload/wrap-reload-script))  ;; Add middleware (1 line)

(defn app-dev []
  ;; Wrap with Ring's reload middleware (1 line)
  (wrap-reload #'app {:dirs ["src" "resources"]}))
```

### 3. Start Server with File Watcher (2 lines)

```clojure
(defn start-server! [port]
  (let [is-dev? (= "dev" (System/getenv "ENV"))
        handler (if is-dev? (app-dev) #'app)]

    ;; Start file watcher in dev mode (2 lines)
    (when is-dev?
      (reload/start-file-watcher! ["src" "resources"]
                                  #{"clj" "css" "js" "html"}))

    (jetty/run-jetty handler {:port port :join? false})))
```

### 4. Run with ENV=dev

```bash
ENV=dev clojure -M:web
```

**That's it!** Now edit any `.clj`, `.css`, `.js`, or `.html` file and your browser automatically refreshes.

## How It Works

```
┌─────────────────────────────────────────────────────────────┐
│  Developer saves file (src/views/home.clj)                  │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  Hawk file watcher detects change                           │
│  (browser-reload.core/start-file-watcher!)                  │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  Updates @last-reload-time atom                             │
│  (browser-reload.core/trigger-reload!)                      │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  Browser polls /dev/reload-check every 1s                   │
│  (injected JavaScript via wrap-reload-script)               │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  JavaScript detects timestamp change → window.location.reload() │
└─────────────────────────────────────────────────────────────┘
```

**Two types of reload:**
1. **Server-side** (Ring's `wrap-reload`) - Recompiles .clj files on each request
2. **Browser-side** (this library) - Automatically refreshes browser when files change

## API Reference

### `start-file-watcher!`

Start watching files for changes.

```clojure
(reload/start-file-watcher! paths extensions)
```

**Args:**
- `paths` - Vector of directories to watch (e.g., `["src" "resources"]`)
- `extensions` - Set of file extensions to trigger on (e.g., `#{"clj" "css" "js"}`)

**Returns:** `:watching`

**Example:**
```clojure
(reload/start-file-watcher! ["src" "resources/public"]
                            #{"clj" "css" "js" "html"})
```

### `stop-file-watcher!`

Stop the file watcher.

```clojure
(reload/stop-file-watcher!)
```

### `trigger-reload!`

Manually trigger a browser reload from the REPL.

```clojure
(reload/trigger-reload!)
```

Useful for forcing a reload without changing files.

### `reload-check-handler`

Ring handler for the `/dev/reload-check` endpoint.

```clojure
["/dev/reload-check" {:get reload/reload-check-handler}]
```

Returns the current reload timestamp. Browser JavaScript polls this.

### `wrap-reload-script`

Ring middleware that injects browser reload JavaScript.

```clojure
(def app
  (-> handler
      reload/wrap-reload-script))
```

Injects polling JavaScript into HTML responses automatically.

## Integration Example

### Before: Manual Refresh Required 😩

```bash
clojure -M:web
# Edit src/my_app/server.clj
# Alt-Tab to browser
# Cmd-R to reload  ← Manual step, 100x per day
```

### After: Auto-Reload Bliss ✨

```bash
ENV=dev clojure -M:web
# Edit src/my_app/server.clj OR resources/public/css/style.css
# Browser auto-refreshes within 1 second  ← NO MANUAL STEP! ✨
```

**Total integration effort:** Add 3 dependencies + 6 lines of code

See [docs/INTEGRATION_EXAMPLE.md](docs/INTEGRATION_EXAMPLE.md) for complete before/after comparison.

## Advanced Usage

### Component-Based Systems

If using Stuart Sierra's Component library:

```clojure
(ns my-app.components.web-server
  (:require [browser-reload.core :as reload]
            [com.stuartsierra.component :as component]))

(defrecord WebServer [config handler server]
  component/Lifecycle

  (start [this]
    (when (= "dev" (System/getenv "ENV"))
      (reload/start-file-watcher! ["src" "resources"] #{"clj" "css" "js"}))
    (assoc this :server (start-jetty handler config)))

  (stop [this]
    (reload/stop-file-watcher!)
    (when server (.stop server))
    (assoc this :server nil)))
```

### Custom File Extensions

Watch any file type:

```clojure
(reload/start-file-watcher! ["src" "resources" "templates"]
                            #{"clj" "cljs" "css" "js" "html" "edn" "sql"})
```

### Manual REPL Workflow

```clojure
;; Start watcher manually
(reload/start-file-watcher! ["src"] #{"clj"})

;; Force a reload from REPL
(reload/trigger-reload!)

;; Stop watcher
(reload/stop-file-watcher!)
```

## Development vs Production

### Development Mode (ENV=dev)

```bash
ENV=dev clojure -M:web
```

- File watcher active
- Browser auto-reload enabled
- Server-side code reloading enabled
- Slight overhead from file watching

### Production Mode (ENV unset)

```bash
clojure -M:web
```

- No file watching
- No browser reload script
- Direct handler execution
- Maximum performance

## FAQ

**Q: Does this work with HTMX/partial page updates?**

A: Yes! The middleware only injects the reload script into full HTML pages (responses with `text/html` content type). HTMX partial responses are unaffected.

**Q: What about WebSocket-based reload (like Figwheel)?**

A: This library uses simple HTTP polling (1 request/second) instead of WebSockets for simplicity and reliability. The overhead is negligible during development.

**Q: Can I use this with ClojureScript?**

A: Yes! Just watch `.cljs` files:
```clojure
(reload/start-file-watcher! ["src"] #{"clj" "cljs" "css"})
```

**Q: Does this require npm/webpack/etc?**

A: No! Pure Clojure solution. No Node.js, no build tools.

**Q: How do I disable in production?**

A: Just don't set `ENV=dev`. The file watcher won't start and the middleware won't inject the script.

## License

MIT License

## Credits

Inspired by:
- Ring's `wrap-reload` for server-side reloading
- Figwheel's auto-reload for ClojureScript
- LiveReload for the browser refresh UX
