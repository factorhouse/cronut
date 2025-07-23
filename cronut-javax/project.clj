(defproject io.factorhouse/cronut-javax "1.0.0"

  :description "A Clojure companion to Quartz"

  :url "https://github.com/factorhouse/cronut"

  :license {:name "Apache 2.0 License"
            :url  "https://github.com/factorhosue/slipway/blob/main/LICENSE"}

  :plugins [[dev.weavejester/lein-cljfmt "0.13.1"]]

  :dependencies [[org.clojure/clojure "1.12.1"]
                 [org.clojure/tools.logging "1.3.0"]
                 [org.quartz-scheduler/quartz "2.4.0" :exclusions [org.slf4j/slf4j-api]]]

  :profiles {:dev   {:resource-paths ["dev-resources"]
                     :dependencies   [[ch.qos.logback/logback-classic "1.3.15"]
                                      [org.slf4j/slf4j-api "2.0.17"]
                                      [org.clojure/core.async "1.8.741"]
                                      [clj-kondo "2025.06.05"]]}
             :smoke {:pedantic? :abort}}

  :aliases {"check"  ["with-profile" "+smoke" "check"]
            "kondo"  ["with-profile" "+smoke" "run" "-m" "clj-kondo.main" "--lint" "src:src:test:test" "--parallel"]
            "fmt"    ["with-profile" "+smoke" "cljfmt" "check"]
            "fmtfix" ["with-profile" "+smoke" "cljfmt" "fix"]}

  :source-paths ["src"]
  :test-paths ["test"]

  :pedantic? :warn)
