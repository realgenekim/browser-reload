# browser-reload Integration Example

Side-by-side comparison showing exactly what changes when adding browser-reload to an existing Ring app.

## Before: Manual Refresh Required 😩

### `deps.edn`

```clojure
{:deps {org.clojure/clojure {:mvn/version "1.12.0"}
        ring/ring {:mvn/version "1.12.2"}
        metosin/reitit {:mvn/version "0.7.0"}
        hiccup/hiccup {:mvn/version "2.0.0"}}

 :aliases {:web {:main-opts ["-m" "my-app.server"]}}}
```

### `src/my_app/server.clj`

```clojure
(ns my-app.server
  (:require [reitit.ring :as reitit]
            [ring.adapter.jetty :as jetty]
            [hiccup2.core :as h]))

(defn home-handler [_]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (str (h/html [:html
                       [:head [:title "My App"]]
                       [:body [:h1 "Hello World"]]]))})

(def routes
  [["/" {:get home-handler}]])

(def app
  (reitit/ring-handler
    (reitit/router routes)))

(defonce server (atom nil))

(defn start-server! [port]
  (when @server (.stop @server))
  (reset! server (jetty/run-jetty #'app {:port port :join? false}))
  (println "Server started on port" port))

(defn -main [& _]
  (start-server! 3000))
```

### Running

```bash
clojure -M:web
# Edit src/my_app/server.clj
# Alt-Tab to browser
# Cmd-R to reload  ← Manual step, 100x per day
```

---

## After: Auto-Reload Bliss ✨

### `deps.edn` (3 lines added)

```clojure
{:deps {org.clojure/clojure {:mvn/version "1.12.0"}
        ring/ring {:mvn/version "1.12.2"}
        metosin/reitit {:mvn/version "0.7.0"}
        hiccup/hiccup {:mvn/version "2.0.0"}}

 :aliases {:web {:extra-deps {;; ⬇ ADD THESE 3 LINES
                              ring/ring-devel {:mvn/version "1.12.2"}
                              hawk/hawk {:mvn/version "0.2.11"}
                              genek/browser-reload {:git/url "https://github.com/realgenekim/browser-reload"
                                                   :git/sha "LATEST-SHA"}}
                 :main-opts ["-m" "my-app.server"]}}}
```

### `src/my_app/server.clj` (6 lines added)

```clojure
(ns my-app.server
  (:require [browser-reload.core :as reload]  ;; ⬅ ADD: Import browser-reload
            [reitit.ring :as reitit]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.reload :refer [wrap-reload]]  ;; ⬅ ADD: Import wrap-reload
            [hiccup2.core :as h]))

(defn home-handler [_]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (str (h/html [:html
                       [:head
                        [:title "My App"]
                        [:link {:rel "stylesheet" :href "/css/style.css"}]]
                       [:body [:h1 "Hello World"]]]))})

(def routes
  [["/" {:get home-handler}]
   ["/dev/reload-check" {:get reload/reload-check-handler}]])  ;; ⬅ ADD: Reload endpoint

(def app
  (-> (reitit/ring-handler (reitit/router routes))
      reload/wrap-reload-script))  ;; ⬅ ADD: Inject reload JavaScript

(defn app-dev []  ;; ⬅ ADD: Development wrapper
  (wrap-reload #'app {:dirs ["src" "resources"]}))

(defonce server (atom nil))

(defn start-server! [port]
  (when @server (.stop @server))

  (let [is-dev? (= "dev" (System/getenv "ENV"))
        handler (if is-dev? (app-dev) #'app)]

    ;; ⬇ ADD THESE 2 LINES: Start file watcher in dev mode
    (when is-dev?
      (reload/start-file-watcher! ["src" "resources"] #{"clj" "css" "js"}))

    (reset! server (jetty/run-jetty handler {:port port :join? false}))
    (println (str "Server started on port " port
                  (when is-dev? " (AUTO-RELOAD ENABLED)")))))

(defn -main [& _]
  (start-server! 3000))
```

### Running

```bash
ENV=dev clojure -M:web
# Edit src/my_app/server.clj OR resources/public/css/style.css
# Browser auto-refreshes within 1 second  ← NO MANUAL STEP! ✨
```

---

## What Changed? (Summary)

### `deps.edn` Changes
1. Added `ring/ring-devel` - Provides `wrap-reload` middleware
2. Added `hawk/hawk` - File watching library
3. Added `genek/browser-reload` - This library

### `server.clj` Changes
1. **Import browser-reload** - `[browser-reload.core :as reload]`
2. **Import wrap-reload** - `[ring.middleware.reload :refer [wrap-reload]]`
3. **Add reload route** - `["/dev/reload-check" {:get reload/reload-check-handler}]`
4. **Wrap with middleware** - `reload/wrap-reload-script`
5. **Create dev wrapper** - `(defn app-dev [] (wrap-reload #'app ...))`
6. **Start file watcher** - `(reload/start-file-watcher! ...)`

### Total Lines Added: **9 lines**

### Result
- Edit any `.clj` file → Browser refreshes (Ring reload + browser-reload)
- Edit any `.css` file → Browser refreshes (browser-reload)
- Edit any `.js` file → Browser refreshes (browser-reload)
- **No manual Cmd-R needed ever again**

---

## Advanced: Component-Based Integration

If using Stuart Sierra's Component library:

```clojure
(ns my-app.components.web-server
  (:require [browser-reload.core :as reload]
            [com.stuartsierra.component :as component]
            [ring.adapter.jetty :as jetty]))

(defrecord WebServer [config handler server file-watcher]
  component/Lifecycle

  (start [this]
    (let [port (get-in config [:web :port] 3000)
          is-dev? (= "dev" (System/getenv "ENV"))]

      ;; Start file watcher if dev mode
      (when is-dev?
        (reload/start-file-watcher! ["src" "resources"] #{"clj" "css" "js"}))

      (assoc this :server
             (jetty/run-jetty handler {:port port :join? false}))))

  (stop [this]
    ;; Stop file watcher
    (reload/stop-file-watcher!)

    ;; Stop server
    (when server (.stop server))

    (assoc this :server nil)))
```

Integration is the same - just add the file watcher start/stop to your component lifecycle.

---

## Files Structure (Minimal Example)

```
my-app/
├── deps.edn                       ← Add 3 dependencies
├── src/
│   └── my_app/
│       └── server.clj             ← Add 6 lines
└── resources/
    └── public/
        ├── css/
        │   └── style.css          ← Edit this → Auto-reload!
        └── js/
            └── app.js             ← Edit this → Auto-reload!
```

**That's the entire integration!**

No config files, no build tools, no webpack, no npm. Just add the dependency and 6 lines of code.
