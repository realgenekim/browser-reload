# browser-reload

Sublime browser auto-reload for Clojure web development. Edit → Save → Browser refreshes automatically.

If you love the incredibly fast and sublime feedback loop that tools like Figwheel or Shadow-CLJS provide, and wish you could get that same immediacy while working on server-side Clojure, this library gives you exactly that.

Zero-friction development experience combining server-side code reloading (Ring’s `wrap-reload`) with just the tiniest amount of injected JavaScript to enable browser refresh on all file changes.

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
                              browser-reload/browser-reload
                                {:git/url "https://github.com/realgenekim/browser-reload"
                                 :git/tag "LATEST"}}
                 :jvm-opts ["-DENV=dev"]}}}
```

**Dependencies explained:**
- `ring/ring-devel` - Provides `wrap-reload` middleware for **server-side** namespace reloading (picks up code changes without JVM restart). This is a development-only dependency that YOU add to your project.
- `browser-reload/browser-reload` - Provides `wrap-reload-script` middleware for **browser-side** auto-refresh (automatically refreshes browser when files change). Already includes `hawk` for file watching.

**Why `ring/ring-devel` isn't included in browser-reload's deps.edn:**
- It's a development-only tool (not needed in production)
- Users might already have it or use alternatives
- Keeps browser-reload's dependency footprint minimal
- You control when/how to add development dependencies to your project

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

;; Base Ring handler (production-ready, no dev overhead)
(def app
  (reitit/ring-handler (reitit/router routes)))

;; Development handler with dual auto-reload support:
;; 1. wrap-reload-script: Injects browser JavaScript that polls /dev/reload-check
;;    → Browser auto-refreshes when server detects file changes
;;    → Includes no-cache headers to prevent reload loops
;; 2. wrap-reload: Server-side namespace reloading on each request
;;    → Clojure code changes take effect without JVM restart
;; Both are required: wrap-reload ensures server code is fresh,
;; wrap-reload-script ensures browser displays the fresh code
(def app-dev
  (-> #'app  ;; Use var reference for REPL-driven development
      reload/wrap-reload-script      ;; Browser-side: auto-refresh (includes no-cache)
      (wrap-reload {:dirs ["src" "resources"]})))  ;; Server-side: reload namespaces
```

### 3. Start Server with File Watcher (2 lines)

```clojure
(defn start-server! [port]
  (let [is-dev? (= "dev" (System/getenv "ENV"))
        handler (if is-dev? app-dev app)]  ;; app-dev is a def, not a function

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
│  App prepare hooks settle or veto → guarded page reload     │
└─────────────────────────────────────────────────────────────┘
```

### Protect stateful editors before reload

The default remains zero-configuration: pages with no listener reload
immediately. Stateful applications can delay or veto navigation by listening
for `browser-reload:prepare` and registering durable settlement work:

```javascript
window.addEventListener('browser-reload:prepare', event => {
  event.detail.waitUntil(settleVisibleEditorDurably());
});
```

The page reloads only after every registered promise succeeds. A rejection,
`false` result, `event.detail.veto(reason)`, `event.preventDefault()`, or timeout
blocks the reload and emits `browser-reload:blocked`. The watcher retries on a
later poll; it never reloads merely because settlement failed.

Applications that provide their own SSE/CSS refresh path can disable polling
without monkey-patching callback names:

```javascript
brStopReloadPolling();
```

This lifecycle is intentionally application-neutral. The application decides
what “settled” means: for an editor it should normally include visible bytes,
document identity, revision, and a durable acknowledgement.

## Why Two Reload Mechanisms?

You need **both** Ring's `wrap-reload` and `browser-reload` for a complete development experience:

### 1. `wrap-reload` (Ring) - Server-Side Code Reloading
```clojure
(wrap-reload #'app {:dirs ["src" "resources"]})
```
- **What it does**: Reloads your Clojure namespace code
- **Where it runs**: On the server (JVM)
- **Purpose**: Picks up code changes without restarting the server
- **How it works**: Reloads namespaces on each HTTP request

### 2. `wrap-reload-script` (this library) - Browser-Side Auto-Refresh
```clojure
(reload/wrap-reload-script handler)
```
- **What it does**: Injects JavaScript that polls for changes
- **Where it runs**: In the browser
- **Purpose**: Automatically refreshes the browser when files change
- **How it works**: Polls `/dev/reload-check` endpoint every 1 second

### The Full Workflow
```
User edits file (e.g., handler.clj)
         ↓
Ring's wrap-reload detects change
         ↓
Server reloads namespace on next request
         ↓
browser-reload's file watcher detects change
         ↓
Browser auto-refreshes via polling
         ↓
User sees updated page immediately
```

**Without `wrap-reload`**: Browser refreshes but shows old code (JVM hasn't reloaded) ❌
**Without `wrap-reload-script`**: Code reloads but browser doesn't refresh (manual F5 needed) ❌
**With both**: Edit → Save → See changes automatically ✅

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

## Infinite Reload Loop Prevention

The `wrap-reload-script` middleware automatically includes `Cache-Control: no-store, must-revalidate` headers to prevent infinite reload loops after server restarts.

### Why This Is Needed

Without no-cache headers, this can happen:

1. Browser caches HTML with embedded reload JavaScript containing timestamp `T1`
2. Server restarts → reload atom gets new timestamp `T2`
3. Browser reloads → fetches CACHED HTML (still has `T1`)
4. JavaScript polls endpoint → sees `T2 > T1` → reloads again
5. Loop repeats forever

The no-cache headers ensure the browser always fetches fresh HTML from the server, so the embedded timestamp always matches the server's current timestamp.
