(ns cronut.integration-test
  (:require [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [cronut.integrant :as cig]
            [integrant.core :as ig])
  (:import (java.util UUID)
           (org.quartz Job)))

(defrecord TestDefrecordJobImpl [identity description recover? durable? test-dep disallowConcurrentExecution?]
  Job
  (execute [this _job-context]
    (log/info "Defrecord Impl:" this)))

(defmethod ig/init-key :dep/one
  [_ config]
  config)

(defmethod ig/init-key :test.job/one
  [_ config]
  (reify Job
    (execute [_this _job-context]
      (log/info "Reified Impl:" config))))

(defmethod ig/init-key :test.job/two
  [_ config]
  (map->TestDefrecordJobImpl config))

(defmethod ig/init-key :test.job/three
  [_ config]
  (reify Job
    (execute [_this _job-context]
      (let [rand-id (str (UUID/randomUUID))]
        (log/info rand-id "Reified Impl (Job Delay 7s):" config)
        (async/<!! (async/timeout 7000))
        (log/info rand-id "Finished")))))

(defn init-system
  "Convenience for starting integrant systems with cronut data-readers"
  ([]
   (init-system (slurp (io/resource "config.edn"))))
  ([config]
   (init-system config nil))
  ([config readers]
   (ig/init (ig/read-string {:readers (merge cig/data-readers readers)} config))))