(defproject com.troy-west/cronut "0.2.1-SNAPSHOT"

  :description "Clojure Scheduled Execution via Quartzite and Integrant"

  :url "https://github.com/troy-west/cronut"

  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v20.html"}

  :plugins [[lein-cljfmt "0.5.7" :exclusions [org.clojure/clojure]]
            [jonase/eastwood "0.2.5" :exclusions [org.clojure/clojure]]
            [lein-kibit "0.1.6" :exclusions [org.clojure/clojure org.clojure/tools.reader]]]

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.logging "0.4.1"]
                 [org.quartz-scheduler/quartz "2.3.0" :exclusions [org.slf4j/slf4j-api]]
                 [integrant "0.6.3"]]

  :profiles {:dev {:resource-paths ["test-resources"]
                   :dependencies   [[ch.qos.logback/logback-classic "1.2.3"]]}}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :aliases {"smoke" ["do" ["clean"] ["check"] ["test"] ["kibit"] ["cljfmt" "check"] ["eastwood"]]}

  :pedantic? :abort)
