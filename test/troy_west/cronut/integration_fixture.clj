(ns troy-west.cronut.integration-fixture
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [troy-west.cronut]
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