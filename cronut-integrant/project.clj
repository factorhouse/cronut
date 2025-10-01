(defproject io.factorhouse/cronut-integrant "1.0.0"

  :description "Integrant bindings for Cronut"

  :url "https://github.com/factorhouse/cronut"

  :license {:name "Apache 2.0 License"
            :url  "https://github.com/factorhosue/slipway/blob/main/LICENSE"}

  :plugins [[dev.weavejester/lein-cljfmt "0.13.4"]]

  :dependencies [[org.clojure/clojure "1.12.3"]
                 [org.clojure/tools.logging "1.3.0"]]

  :profiles {:dev     {:resource-paths ["dev-resources"]
                       :dependencies   [[integrant "0.13.1"]
                                        [ch.qos.logback/logback-classic "1.5.19"]
                                        [org.slf4j/slf4j-api "2.0.17"]
                                        [org.clojure/core.async "1.8.741"]
                                        [clj-kondo "2025.09.22" :exclusions [org.clojure/tools.reader]]]}
             :jakarta {:dependencies [[io.factorhouse/cronut "1.0.0"]]}
             :javax   {:dependencies [[io.factorhouse/cronut-javax "1.0.0"]]}
             :smoke   {:pedantic? :abort}}

  :aliases {"check"  ["with-profile" "+smoke,+jakarta" "check"]
            "kondo"  ["with-profile" "+smoke,+jakarta" "run" "-m" "clj-kondo.main" "--lint" "src:src:test:test" "--parallel"]
            "fmt"    ["with-profile" "+smoke,+jakarta" "cljfmt" "check"]
            "fmtfix" ["with-profile" "+smoke" "cljfmt" "fix"]}

  :source-paths ["src"]
  :test-paths ["test"]

  :pedantic? :warn)
