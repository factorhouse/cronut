(ns cronut-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.tools.logging :as log]
            [cronut :as cronut]
            [cronut.trigger :as trigger])
  (:import (org.quartz Job Trigger)))

(defrecord TestDefrecordJobImpl [identity description recover? durable? test-dep disallowConcurrentExecution?]
  Job
  (execute [this _job-context]
    (log/info "Defrecord Impl:" this)))

(deftest concurrent-execution-disallowed

  (is (not (cronut/concurrent-execution-disallowed? (cronut/scheduler {}))))

  (is (cronut/concurrent-execution-disallowed? (cronut/scheduler {:concurrent-execution-disallowed? true}))))

(deftest scheduling

  (let [scheduler (cronut/scheduler {})
        _         (cronut/clear scheduler)
        trigger   (cronut/schedule-job scheduler
                                       (trigger/interval 2000)
                                       (map->TestDefrecordJobImpl {:identity    ["test-group" "test-name"]
                                                                   :description "test job"
                                                                   :recover?    true
                                                                   :durable?    false}))]
    (is (instance? Trigger trigger))

    (is (cronut/unschedule-job scheduler trigger))))