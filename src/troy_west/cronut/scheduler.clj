(ns troy-west.cronut.scheduler
  (:require [clojure.tools.logging :as log])
  (:import (org.quartz.impl StdSchedulerFactory)
           (org.quartz Scheduler JobExecutionContext JobBuilder TriggerBuilder SimpleScheduleBuilder JobDetail Job)
           (java.time ZoneId)
           (org.quartz.spi JobFactory TriggerFiredBundle)))

(def utc-zone (ZoneId/of "UTC"))

(defrecord ProxyJob [proxied-job]
  Job
  (execute [_ job-context]
    (.execute ^Job proxied-job job-context)))

(defn initialize
  [config]
  (let [{:keys [update-check? time-zone] :or {time-zone utc-zone}} config
        scheduler (StdSchedulerFactory/getDefaultScheduler)]
    (log/infof "initializing scheduler in %s" time-zone)
    (when-not update-check?
      (System/setProperty "org.terracotta.quartz.skipUpdateCheck" "true")
      (log/infof "quartz update check disabled" time-zone))
    (.start scheduler)
    (.setJobFactory scheduler
                    (reify JobFactory
                      (newJob [_ bundle _]
                        (let [job-detail (.getJobDetail ^TriggerFiredBundle bundle)
                              job-data   (.getJobDataMap job-detail)]
                          (->ProxyJob (reify Job
                                        (execute [this job-detail]
                                          (prn "whoo!!"))))))))
    (.scheduleJob scheduler
                  (-> (JobBuilder/newJob)
                      (.ofType Job)
                      (.build))
                  (-> (TriggerBuilder/newTrigger)
                      (.startNow)
                      (.withSchedule (-> (SimpleScheduleBuilder/simpleSchedule)
                                         (.withIntervalInSeconds 2)
                                         (.repeatForever)))
                      (.build)))
    scheduler))

(defn shutdown
  [scheduler]
  (.shutdown ^Scheduler scheduler))