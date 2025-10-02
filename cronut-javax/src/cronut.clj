(ns cronut
  (:refer-clojure :exclude [proxy])
  (:require [clojure.tools.logging :as log]
            [cronut.job :as job]
            [cronut.trigger :as trigger])
  (:import (org.quartz JobDetail JobKey Scheduler Trigger TriggerBuilder)
           (org.quartz.impl StdSchedulerFactory)))

(defn scheduler
  "Create a new Quartz scheduler:
     :concurrent-execution-disallowed? - run all jobs with @DisableConcurrentExecution (default false)
     :update-check? - check for Quartz updates on system startup (default: false)"
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

(defn concurrent-execution-disallowed?
  "Determine if a scheduler has concurrent job execution disallowed"
  [^Scheduler scheduler]
  (= "true" (get (.getContext scheduler) "concurrentExecutionDisallowed?")))

(defn get-detail
  "Get the job detail for a key"
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

(defn pause-job
  ([^Scheduler scheduler name group]
   (.pauseJob scheduler (job/key name group)))
  ([^Scheduler scheduler ^Trigger trigger]
   (.pauseJob scheduler (.getJobKey trigger))))

(defn resume-job
  ([^Scheduler scheduler name group]
   (.resumeJob scheduler (job/key name group)))
  ([^Scheduler scheduler ^Trigger trigger]
   (.resumeJob scheduler (.getJobKey trigger))))

(defn unschedule-job
  ([^Scheduler scheduler name group]
   (.unscheduleJob scheduler (trigger/key name group)))
  ([^Scheduler scheduler ^Trigger trigger]
   (.unscheduleJob scheduler (.getKey trigger))))

(defn delete-job
  ([^Scheduler scheduler name group]
   (.deleteJob scheduler (job/key name group)))
  ([^Scheduler scheduler ^Trigger trigger]
   (.deleteJob scheduler (.getJobKey trigger))))

(defn pause-trigger
  ([^Scheduler scheduler name group]
   (.pauseTrigger scheduler (trigger/key name group)))
  ([^Scheduler scheduler ^Trigger trigger]
   (.pauseTrigger scheduler (.getKey trigger))))

(defn resume-trigger
  ([^Scheduler scheduler name group]
   (.resumeTrigger scheduler (trigger/key name group)))
  ([^Scheduler scheduler ^Trigger trigger]
   (.resumeTrigger scheduler (.getKey trigger))))

(defn start
  [^Scheduler scheduler]
  (.start scheduler)
  scheduler)

(defn start-delayed
  [^Scheduler scheduler delay-s]
  (.startDelayed scheduler delay-s)
  scheduler)

(defn standby
  [^Scheduler scheduler]
  (.standby scheduler)
  scheduler)

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

(defn shutdown
  ([scheduler]
   (.shutdown ^Scheduler scheduler)
   scheduler)
  ([scheduler wait?]
   (.shutdown ^Scheduler scheduler wait?)
   scheduler))