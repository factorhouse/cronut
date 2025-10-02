(ns cronut.job-test
  (:require [clojure.test :refer [deftest is]]
            [cronut.job :as job])
  (:import (cronut.job ProxyJob SerialProxyJob)
           (org.quartz Job)))

(def job-keys [:fullName
               :jobClass
               :description
               :durable
               ;; note: this spelling mistake remains in 2.4.0, fixed in 2.5.0
               ;; https://github.com/quartz-scheduler/quartz/pull/1165
               :concurrentExectionDisallowed
               :jobDataMap])

(def reify-job (reify Job
                 (execute [_ _])))

(deftest job-detail-meta

  (is (= {:fullName                     "group2.name1"
          :jobClass                     SerialProxyJob
          :description                  "desc3"
          :durable                      true
          :concurrentExectionDisallowed true
          :jobDataMap                   {"job-impl" {:description                    "desc3"
                                                     :disallow-concurrent-execution? true
                                                     :durable?                       true
                                                     :identity                       ["name1" "group2"]
                                                     :recover?                       true}}}
         (-> (job/detail {:identity                       ["name1" "group2"]
                          :description                    "desc3"
                          :durable?                       true
                          :recover?                       true
                          :disallow-concurrent-execution? true} false)
             (bean)
             (select-keys job-keys)))))

(deftest job-detail-concurrency-reify-style

  ;; global concurrentExecutionDisallowed? = false
  (is (= {:jobClass                      ProxyJob
          :description                   nil
          :durable                       false
          :concurrentExectionDisallowed false
          :jobDataMap                    {"job-impl" reify-job}}
         (-> (job/detail reify-job false)
             (bean)
             (select-keys job-keys)
             (dissoc :fullName))))

  ;; global concurrentExecutionDisallowed? = true
  (is (= {:jobClass                      SerialProxyJob
          :description                   nil
          :durable                       false
          :concurrentExectionDisallowed true
          :jobDataMap                    {"job-impl" reify-job}}
         (-> (job/detail reify-job true)
             (bean)
             (select-keys job-keys)
             (dissoc :fullName)))))

(deftest job-detail-concurrency-defrecord-style

  ;; global concurrentExecutionDisallowed? = false
  ;; job disallowConcurrentExecution? = false
  (is (= {:jobClass                      ProxyJob
          :description                   nil
          :durable                       false
          :concurrentExectionDisallowed false
          :jobDataMap                    {"job-impl" {}}}
         (-> (job/detail {} false)
             (bean)
             (select-keys job-keys)
             (dissoc :fullName))))

  ;; global concurrentExecutionDisallowed? true
  ;; job disallowConcurrentExecution? = false
  (is (= {:jobClass                      SerialProxyJob
          :description                   nil
          :durable                       false
          :concurrentExectionDisallowed true
          :jobDataMap                    {"job-impl" {}}}
         (-> (job/detail {} true)
             (bean)
             (select-keys job-keys)
             (dissoc :fullName))))

  ;; global concurrentExecutionDisallowed? = false
  ;; job disallowConcurrentExecution? = true
  (is (= {:jobClass                      SerialProxyJob
          :description                   nil
          :durable                       false
          :concurrentExectionDisallowed true
          :jobDataMap                    {"job-impl" {:disallow-concurrent-execution? true}}}
         (-> (job/detail {:disallow-concurrent-execution? true} false)
             (bean)
             (select-keys job-keys)
             (dissoc :fullName))))

  ;; global concurrentExecutionDisallowed? = true
  ;; job disallowConcurrentExecution? = true
  (is (= {:jobClass                      SerialProxyJob
          :description                   nil
          :durable                       false
          :concurrentExectionDisallowed true
          :jobDataMap                    {"job-impl" {:disallow-concurrent-execution? true}}}
         (-> (job/detail {:disallow-concurrent-execution? true} false)
             (bean)
             (select-keys job-keys)
             (dissoc :fullName)))))
