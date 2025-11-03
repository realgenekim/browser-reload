(ns browser-reload.core
  "Browser auto-reload for development
  
  Watches files for changes and automatically refreshes browser.
  Only active in development mode (ENV=dev).
  
  Extracted from podcast-player-rn project for reusability.
  
  Usage:
    1. Add route: [GET /dev/reload-check reload/reload-check-handler]
    2. Wrap app: (reload/wrap-reload-script app)
    3. On server start: (reload/start-file-watcher! paths extensions)
    4. On server stop: (reload/stop-file-watcher!)
  
  The middleware automatically injects JavaScript into HTML responses
  that polls /dev/reload-check every second and reloads on changes."
  (:require
   [clojure.string :as str]
   [hawk.core :as hawk]
   [hiccup2.core :as h]
   [taoensso.timbre :as log])
  (:import
   (java.io File)))

;; ========================================
;; State & Configuration
;; ========================================

(def last-reload-time
  "Timestamp of last reload trigger. Browser polls this."
  (atom (System/currentTimeMillis)))

;; Active file watcher instance, or nil if not running
(defonce file-watcher (atom nil))

;; ========================================
;; Public API
;; ========================================

(defn trigger-reload!
  "Trigger browser reload by updating timestamp.
  
  Call manually from REPL to force a browser refresh:
    (reload/trigger-reload!)
  
  Returns the new timestamp."
  []
  (reset! last-reload-time (System/currentTimeMillis))
  (log/info :trigger-reload! :triggered
    :timestamp @last-reload-time)
  (println "🔥 Browser reload triggered!" @last-reload-time)
  @last-reload-time)

(defn start-file-watcher!
  "Start watching files for changes and trigger reload automatically.
  
  Args:
    watch-paths - Vector of paths to watch (e.g. [\"src\" \"resources/public\"])
    extensions  - Set of file extensions to trigger on (e.g. #{\"clj\" \"css\" \"js\"})
  
  The watcher:
  - Only triggers on :modify events
  - Filters by file extension
  - Automatically calls trigger-reload! on relevant changes
  
  If a watcher is already running, stops it first."
  [watch-paths extensions]
  (when @file-watcher
    (log/info :start-file-watcher! :stopping-existing)
    (println "Stopping existing file watcher...")
    (hawk/stop! @file-watcher))

  (log/info :start-file-watcher! :starting
    :paths watch-paths
    :extensions (sort extensions))
  (println "\n========================================")
  (println "🔍 Starting File Watcher")
  (println "========================================")
  (println "Watching paths:" watch-paths)
  (println "Extensions:    " (str/join ", " (sort extensions)))
  (println "========================================\n")

  (reset! file-watcher
          (hawk/watch!
            [{:paths watch-paths
              :handler (fn [ctx event]
                         ;; Only process :modify events
                         (when (= (:kind event) :modify)
                           (let [^File file (:file event)
                                 path (.getPath file)
                                 filename (.getName file)
                                 ext (when (str/includes? filename ".")
                                       (last (str/split filename #"\.")))]

                             ;; Only trigger reload for relevant file types
                             (when (contains? extensions ext)
                               (log/info :file-watcher :change-detected
                                 :path path)
                               (println "🔥🔥🔥 CHANGE DETECTED:" path)
                               (println "⚡⚡⚡ TRIGGERING RELOAD!")
                               (trigger-reload!))))
                         ctx)}]))

  (log/info :start-file-watcher! :started)
  (println "✅ File watcher started\n")
  :watching)

(defn stop-file-watcher!
  "Stop the file watcher if running."
  []
  (if-let [watcher @file-watcher]
    (do
      (hawk/stop! watcher)
      (reset! file-watcher nil)
      (log/info :stop-file-watcher! :stopped)
      (println "File watcher stopped."))
    (do
      (log/debug :stop-file-watcher! :already-stopped)
      (println "File watcher not running."))))

;; ========================================
;; Ring Integration
;; ========================================

(defn reload-check-handler
  "HTTP handler for /dev/reload-check endpoint.
  
  Returns current reload timestamp as plain text.
  Includes aggressive no-cache headers to prevent browser caching.
  
  Browser JavaScript polls this endpoint and reloads if value changes."
  [_request]
  {:status 200
   :headers {"Content-Type" "text/plain"
             "Cache-Control" "no-cache, no-store, must-revalidate"
             "Pragma" "no-cache"
             "Expires" "0"}
   :body (str @last-reload-time)})

;; ========================================
;; JavaScript Generation
;; ========================================

(defn- reload-script
  "Generate JavaScript code for browser auto-reload.
  
  The script:
  - Polls /dev/reload-check every 1000ms
  - Compares response to last known timestamp
  - Reloads page if timestamp changed
  - Handles fetch errors gracefully"
  []
  (str
    "let lastReloadTime = " @last-reload-time ";\n"
    "setInterval(async () => {\n"
    "  try {\n"
    "    const resp = await fetch('/dev/reload-check');\n"
    "    const newTime = await resp.text();\n"
    "    if (newTime !== String(lastReloadTime)) {\n"
    "      console.log('🔄 Reloading due to file change...');\n"
    "      window.location.reload();\n"
    "    }\n"
    "  } catch (e) {\n"
    "    console.error('Reload check failed:', e);\n"
    "  }\n"
    "}, 1000);"))

(defn- inject-reload-script
  "Inject reload script into HTML body.
  
  Adds a <script> tag at the end of <body> that polls for changes."
  [html-body]
  (if (and html-body (str/includes? html-body "</body>"))
    (str/replace html-body
                 "</body>"
                 (str "<script>\n" (reload-script) "\n</script></body>"))
    html-body))

;; ========================================
;; Middleware
;; ========================================

(defn wrap-reload-script
  "Middleware that injects browser reload JavaScript into HTML responses.
  
  Only injects if:
  - Response is HTML (Content-Type contains 'text/html')
  - Response body is a string
  - Body contains </body> tag
  
  Usage:
    (def app
      (-> handler
          (reload/wrap-reload-script)))
  
  The injected JavaScript polls /dev/reload-check and reloads on changes."
  [handler]
  (fn [request]
    (let [response (handler request)]
      (if (and response
               (string? (:body response))
               (or (nil? (get-in response [:headers "Content-Type"]))
                   (str/includes? (str (get-in response [:headers "Content-Type"])) "text/html")))
        (update response :body inject-reload-script)
        response))))

;; ========================================
;; REPL Development Helpers
;; ========================================

(comment
  ;; Manual trigger from REPL
  (trigger-reload!)

  ;; Start watcher
  (start-file-watcher! ["src" "resources/public"]
                       #{"clj" "css" "js" "html"})

  ;; Stop watcher
  (stop-file-watcher!)

  ;; Check watcher status
  @file-watcher)
