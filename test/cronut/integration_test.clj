(ns cronut.integration-test
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [cronut :as cronut]
            [cronut.trigger :as trigger])
  (:import (java.util UUID)
           (org.quartz Job)))

(defrecord TestDefrecordJobImpl []
  Job
  (execute [this _job-context]
    (log/info "Defrecord Impl:" this)))

(def reify-job (reify Job
                 (execute [_this _job-context]
                   (let [rand-id (str (UUID/randomUUID))]
                     (log/info rand-id "Reified Impl (Job Delay 7s)")
                     (async/<!! (async/timeout 7000))
                     (log/info rand-id "Finished")))))

;(do (require '[cronut.integration-test :as it])
;    (it/test-system))
(defn test-system
  []
  (let [scheduler (cronut/scheduler {:concurrent-execution-disallowed? true})]
    (cronut/clear scheduler)

    (async/<!! (async/timeout 2000))

    (log/info "scheduling defrecord job on 1s interval")
    (cronut/schedule-job scheduler
                         (trigger/interval 1000)
                         (map->TestDefrecordJobImpl {})
                         {:name    "name1"
                          :group       "group2"
                          :description "test job"
                          :recover?    true
                          :durable?    false})

    ;; demonstrate scheduler can start with jobs, and jobs can start after scheduler
    (cronut/start scheduler)

    (async/<!! (async/timeout 2000))

    ;; demonstrates concurrency disallowed (every second job runs, 10s interval between jobs that should run every 5s)
    (log/info "scheduling reify/7s/no-misfire job on 5s interval")
    (cronut/schedule-job scheduler
                         (trigger/builder {:type    :cron
                                           :cron    "*/5 * * * * ?"
                                           :misfire :do-nothing})
                         reify-job
                         {:name        "name2"
                          :group       "group2"
                          :description "test job 2"
                          :recover?    false
                          :durable?    true})

    (async/<!! (async/timeout 15000))

    (log/info "deleting job group2.name1")
    (cronut/delete-job scheduler "name1" "group2")

    (async/<!! (async/timeout 15000))

    (cronut/shutdown scheduler)))