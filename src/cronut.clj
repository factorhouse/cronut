(ns cronut
  (:refer-clojure :exclude [proxy])
  (:require [clojure.tools.logging :as log]
            [cronut.job :as job]
            [cronut.trigger :as trigger])
  (:import (org.quartz JobDetail Scheduler Trigger TriggerBuilder)
           (org.quartz.impl StdSchedulerFactory)))

(defn scheduler
  "Create a new Quartz scheduler:
     :concurrent-execution-disallowed? - run all jobs with @DisableConcurrentExecution (default false)
     :update-check? - check for Quartz updates on system startup (default: false)"
  ^Scheduler [{:keys [update-check? concurrent-execution-disallowed?]}]
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

(defn schedule-job
  (^Trigger [^Scheduler scheduler ^TriggerBuilder trigger job]
   (schedule-job scheduler trigger job nil))
  (^Trigger [^Scheduler scheduler ^TriggerBuilder trigger job opts]
   (let [cce-disallowed? (concurrent-execution-disallowed? scheduler)
         detail          (job/detail job (update opts :disallow-concurrent-execution? #(or cce-disallowed? %1)))]
     (if-let [^JobDetail previously-scheduled (.getJobDetail scheduler (.getKey detail))]
       (let [built (.build (.forJob trigger previously-scheduled))]
         (log/info "scheduling new trigger for existing job" (str (.getKey previously-scheduled)) opts)
         (.scheduleJob scheduler built)
         built)
       (let [built (.build trigger)]
         (log/info "scheduling new job" (str (.getKey detail)) opts)
         (.scheduleJob scheduler detail built)
         built)))))

(defn schedule-jobs
  [^Scheduler scheduler schedule]
  (log/infof "scheduling [%s] jobs" (count schedule))
  (loop [schedule schedule
         triggers []]
    (if-let [{:keys [^TriggerBuilder trigger job opts]} (first schedule)]
      (recur (rest schedule) (conj triggers (schedule-job scheduler trigger job opts)))
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

;; unschedule-trigger rather than unschedule-job because it works on trigger identity
(defn unschedule-trigger
  ([^Scheduler scheduler name group]
   (.unscheduleJob scheduler (trigger/key name group)))
  ([^Scheduler scheduler ^Trigger trigger]
   (.unscheduleJob scheduler (.getKey trigger))))

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