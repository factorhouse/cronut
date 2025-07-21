(defproject io.factorhouse/cronut-integrant "1.0.0"

  :description "Integrant bindings for Cronut"

  :url "https://github.com/factorhouse/cronut"

  :license {:name "Apache 2.0 License"
            :url  "https://github.com/factorhosue/slipway/blob/main/LICENSE"}

  :plugins [[dev.weavejester/lein-cljfmt "0.13.1"]]

  :dependencies [[org.clojure/clojure "1.12.1"]
                 [org.clojure/tools.logging "1.3.0"]]

  :profiles {:dev     {:resource-paths ["dev-resources"]
                       :dependencies   [[integrant "0.13.1"]
                                        [ch.qos.logback/logback-classic "1.5.18"]
                                        [org.slf4j/slf4j-api "2.0.17"]
                                        [org.clojure/core.async "1.8.741"]
                                        [clj-kondo "2025.06.05"]]}
             :jakarta {:dependencies [[io.factorhouse/cronut "0.2.7"]]} ;; TODO fix up after release
             :javax   {:dependencies [[io.factorhouse/cronut "0.2.7"]]} ;; TODO fix up after release
             :smoke   {:pedantic? :abort}}

  :aliases {"check" ["with-profile" "+smoke,+jakarta" "check"]
            "kondo" ["with-profile" "+smoke,+jakarta" "run" "-m" "clj-kondo.main" "--lint" "src:src:test:test" "--parallel"]
            "fmt"   ["with-profile" "+smoke,+jakarta" "cljfmt" "check"]}

  :source-paths ["src"]
  :test-paths ["test"]

  :pedantic? :warn)
