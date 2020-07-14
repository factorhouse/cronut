(ns troy-west.cronut.integration-fixture
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [troy-west.cronut :as cronut]
            [integrant.core :as ig])
  (:import (org.quartz Job)))

(defrecord TestDefrecordJobImpl [identity description recover? durable? test-dep]
  Job
  (execute [this job-context]
    (log/info "Defrecord Impl:" this)))

(defmethod ig/init-key :dep/one
  [_ config]
  config)

(defmethod ig/init-key :test.job/one
  [_ config]
  (reify Job
    (execute [this job-context]
      (log/info "Reified Impl:" config))))

(defmethod ig/init-key :test.job/two
  [_ config]
  (map->TestDefrecordJobImpl config))

(defn init-system
  "Convenience for starting integrant systems with cronut data-readers"
  ([]
   (init-system (slurp (io/resource "config.edn"))))
  ([config]
   (init-system config nil))
  ([config readers]
   (ig/init (ig/read-string {:readers (merge cronut/data-readers readers)} config))))