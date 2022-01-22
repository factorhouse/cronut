(defproject com.troy-west/cronut "0.2.7-SNAPSHOT"

  :description "Clojure Scheduled Execution via Quartzite and Integrant"

  :url "https://github.com/troy-west/cronut"

  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v20.html"}

  :plugins [[lein-cljfmt "0.8.0" :exclusions [org.clojure/clojure]]]

  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/tools.logging "1.2.4"]
                 [org.quartz-scheduler/quartz "2.3.2" :exclusions [org.slf4j/slf4j-api]]
                 [integrant "0.8.0" :scope "provided"]]

  :profiles {:dev {:resource-paths ["test-resources"]
                   :dependencies   [[ch.qos.logback/logback-classic "1.2.10"]
                                    [org.clojure/core.async "1.5.648"]
                                    [clj-kondo "2022.01.15"]]}}

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
