(defproject com.troy-west/cronut "0.1.0-SNAPSHOT"

  :description "Scheduled Execution via Quartzite and Integrant"

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.logging "0.4.1"]
                 [org.quartz-scheduler/quartz "2.3.0" :exclusions [org.slf4j/slf4j-api]]
                 [integrant "0.6.3"]]

  :profiles {:dev {:resource-paths ["test-resources"]
                   :dependencies   [[ch.qos.logback/logback-classic "1.2.3"]]}}

  :pedantic? :abort)