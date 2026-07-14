(ns browser-reload.core-test
  (:require [browser-reload.core :as reload]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def ^:private reload-script #'reload/reload-script)
(def ^:private inject-reload-script #'reload/inject-reload-script)

(deftest reload-is-guarded-by-an-async-vetoable-lifecycle
  (let [script (reload-script)]
    (testing "applications can settle durable state before navigation"
      (is (str/includes? script "browser-reload:prepare"))
      (is (str/includes? script "waitUntil(promise)"))
      (is (str/includes? script "await brPrepareReload(parsedTime)")))
    (testing "failure is fail-closed and visible"
      (is (str/includes? script "browser-reload:blocked"))
      (is (str/includes? script "prepare timeout"))
      (is (< (.indexOf script "await brPrepareReload(parsedTime)")
             (.indexOf script "window.location.reload()"))))))

(deftest polling-has-an-effective-stop-operation
  (let [script (reload-script)]
    (is (str/includes? script "function brStopReloadPolling()"))
    (is (str/includes? script "clearInterval(brReloadTimerId)"))
    (is (str/includes? script "setInterval(() => brCheckReload(), 1000)"))
    (is (not (str/includes? script "setInterval(brCheckReload, 1000)"))
        "the interval must not retain an unreplaceable callback reference")))

(deftest injection-preserves-the-guarded-runtime
  (let [html (inject-reload-script "<html><body><main>Hi</main></body></html>")]
    (is (str/includes? html "browser-reload:prepare"))
    (is (str/includes? html "brStopReloadPolling"))
    (is (str/includes? html "<main>Hi</main>"))))
