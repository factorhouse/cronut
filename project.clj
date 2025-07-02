(defproject io.factorhouse/cronut "0.2.7"

  :description "Clojure Scheduled Execution via Quartzite and Integrant"

  :url "https://github.com/factorhouse/cronut"

  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v20.html"}

  :plugins [[dev.weavejester/lein-cljfmt "0.13.1"]]

  :dependencies [[org.clojure/clojure "1.12.1"]
                 [org.clojure/tools.logging "1.3.0"]
                 [org.quartz-scheduler/quartz "2.4.0" :exclusions [org.slf4j/slf4j-api]]
                 [integrant "0.13.1" :scope "provided"]]

  :profiles {:dev {:resource-paths ["test-resources"]
                   :dependencies   [[ch.qos.logback/logback-classic "1.3.15"]
                                    [org.clojure/core.async "1.8.741"]
                                    [clj-kondo "2025.06.05"]]}}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :aliases {"smoke" ["do"
                     ["clean"]
                     ["check"]
                     ["test"]
                     ["cljfmt" "check"]
                     ["run" "-m" "clj-kondo.main" "--lint" "src:test" "--parallel"]]}

  :source-paths ["src"]
  :test-paths ["test"]

  :pedantic? :abort)
