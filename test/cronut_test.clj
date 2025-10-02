(ns cronut-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]
            [cronut :as cronut]
            [cronut.trigger :as trigger])
  (:import (cronut.job ProxyJob)
           (org.quartz Job JobKey Trigger)))

(defn reify-job
  [n]
  (reify Job
    (execute [_ _]
      (log/info "Executing job: " n))))

(deftest concurrent-execution-disallowed

  (is (not (cronut/concurrent-execution-disallowed? (cronut/scheduler {}))))

  (is (cronut/concurrent-execution-disallowed? (cronut/scheduler {:concurrent-execution-disallowed? true}))))

(deftest scheduling

  (let [scheduler (cronut/scheduler {:concurrent-execution-disallowed? true})
        _         (cronut/clear scheduler)
        trigger   (cronut/schedule-job scheduler
                                       (trigger/interval 2000)
                                       (reify-job 1)
                                       {:name        "name1"
                                        :group       "group1"
                                        :description "test job"
                                        :recover?    true
                                        :durable?    false})
        trigger2  (cronut/schedule-job scheduler
                                       (trigger/builder {:type      :simple
                                                         :interval  3000
                                                         :time-unit :millis
                                                         :repeat    :forever
                                                         :name      "trigger-name2"
                                                         :group     "group1"})
                                       (reify-job 2)
                                       {:name        "name2"
                                        :group       "group2"
                                        :description "test job"
                                        :recover?    true
                                        :durable?    true})
        trigger3  (cronut/schedule-job scheduler
                                       (cronut.trigger/cron "*/8 * * * * ?")
                                       (reify-job 3)
                                       {:name        "name3"
                                        :group       "group2"
                                        :description "test job"
                                        :recover?    false
                                        :durable?    true})
        trigger4  (cronut/schedule-job scheduler
                                       (trigger/builder {:type  :cron
                                                         :cron  "*/5 * * * * ?"
                                                         :name  "trigger-name4"
                                                         :group "group2"})
                                       (reify-job 4)
                                       {:name        "name4"
                                        :group       "group2"
                                        :description "test job"
                                        :recover?    false
                                        :durable?    false})]
    (is (instance? Trigger trigger))
    (is (instance? Trigger trigger2))
    (is (instance? Trigger trigger3))
    (is (instance? Trigger trigger4))

    (testing "scheduler concurrent-execution-disallowed?"

      ;; concurrent-execution-disallowed? set on the scheduler only
      (is (->> (JobKey. "name1" "group1")
               (.getJobDetail scheduler)
               (.isConcurrentExecutionDisallowed))))

    (testing "unschedule by trigger"
      (is (cronut/unschedule-trigger scheduler trigger))
      ;; second call returns false, no trigger to unschedule
      (is (not (cronut/unschedule-trigger scheduler trigger))))

    (testing "unschedule by trigger identity"
      (is (cronut/unschedule-trigger scheduler "trigger-name2" "group1"))
      ;; second call returns false, no trigger to unschedule
      (is (not (cronut/unschedule-trigger scheduler "trigger-name2" "group1"))))

    (testing "delete by trigger"
      (is (cronut/delete-job scheduler trigger3))

      ;; second call returns false, no job to delete
      (is (not (cronut/delete-job scheduler trigger3))))

    (testing "delete by job identity"
      (is (cronut/delete-job scheduler "name4" "group2"))
      ;; second call returns false, no job to unschedule
      (is (not (cronut/delete-job scheduler "name4" "group2"))))))