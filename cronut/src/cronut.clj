(ns cronut
  (:refer-clojure :exclude [proxy])
  (:require [clojure.tools.logging :as log]
            [cronut.job :as job])
  (:import (org.quartz JobDetail JobKey Scheduler Trigger TriggerBuilder TriggerKey)
           (org.quartz.impl StdSchedulerFactory)))

(defn concurrent-execution-disallowed?
  [^Scheduler scheduler]
  (= "true" (get (.getContext scheduler) "concurrentExecutionDisallowed?")))

(defn get-detail
  [^Scheduler scheduler ^JobKey key]
  (.getJobDetail scheduler key))

(defn schedule-job
  ^Trigger [^Scheduler scheduler ^TriggerBuilder trigger job]
  (let [detail ^JobDetail (job/detail job (concurrent-execution-disallowed? scheduler))]
    (if-let [^JobDetail previously-scheduled (get-detail scheduler (.getKey detail))]
      (let [built (.build (.forJob trigger previously-scheduled))]
        (log/info "scheduling new trigger for existing job" built previously-scheduled)
        (.scheduleJob scheduler built)
        built)
      (let [built (.build trigger)]
        (log/info "scheduling new job" built detail)
        (.scheduleJob scheduler detail built)
        built))))

(defn schedule-jobs
  [^Scheduler scheduler jobs]
  (log/infof "scheduling [%s] jobs" (count jobs))
  (loop [schedule jobs
         triggers []]
    (if-let [{:keys [^TriggerBuilder trigger job]} (first schedule)]
      (recur (rest schedule) (conj triggers (schedule-job scheduler trigger job)))
      triggers)))

(defn scheduler
  [{:keys [update-check? concurrent-execution-disallowed?]}]
  (log/infof "initializing scheduler")
  (when-not update-check?
    (System/setProperty "org.terracotta.quartz.skipUpdateCheck" "true")
    (log/infof "with quartz update check disabled"))
  (let [scheduler (StdSchedulerFactory/getDefaultScheduler)]
    (if concurrent-execution-disallowed?
      (do
        (log/infof "with global concurrent execution disallowed")
        (.put (.getContext scheduler) "concurrentExecutionDisallowed?" "true"))
      (.put (.getContext scheduler) "concurrentExecutionDisallowed?" "false"))
    (.setJobFactory scheduler (job/factory))
    scheduler))

(defn pause-job
  ([^Scheduler scheduler group name]
   (.pauseJob scheduler (JobKey. name group)))
  ([^Scheduler scheduler ^Trigger trigger]
   (.pauseJob scheduler (.getJobKey trigger))))

(defn resume-job
  ([^Scheduler scheduler group name]
   (.resumeJob scheduler (JobKey. name group)))
  ([^Scheduler scheduler ^Trigger trigger]
   (.resumeJob scheduler (.getJobKey trigger))))

(defn pause-trigger
  ([^Scheduler scheduler group name]
   (.pauseTrigger scheduler (TriggerKey. name group)))
  ([^Scheduler scheduler ^Trigger trigger]
   (.pauseTrigger scheduler (.getKey trigger))))

(defn resume-trigger
  ([^Scheduler scheduler group name]
   (.resumeTrigger scheduler (TriggerKey. name group)))
  ([^Scheduler scheduler ^Trigger trigger]
   (.resumeTrigger scheduler (.getKey trigger))))

(defn delete-job
  ([^Scheduler scheduler group name]
   (.deleteJob scheduler (JobKey. name group)))
  ([^Scheduler scheduler ^Trigger trigger]
   (.deleteJob scheduler (.getJobKey trigger))))

(defn unschedule-job
  ([^Scheduler scheduler group name]
   (.unscheduleJob scheduler (TriggerKey. name group)))
  ([^Scheduler scheduler ^Trigger trigger]
   (.unscheduleJob scheduler (.getKey trigger))))

(defn pause-all
  [^Scheduler scheduler]
  (.pauseAll scheduler))

(defn resume-all
  [^Scheduler scheduler]
  (.resumeAll scheduler))

(defn clear
  [^Scheduler scheduler]
  (.clear scheduler)
  scheduler)

(defn start
  [^Scheduler scheduler]
  (.start scheduler)
  scheduler)

(defn shutdown
  [scheduler]
  (.shutdown ^Scheduler scheduler)
  scheduler)