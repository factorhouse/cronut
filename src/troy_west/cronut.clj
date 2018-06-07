(ns troy-west.cronut
  (:refer-clojure :exclude [proxy])
  (:require [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [integrant.core :as ig])
  (:import (org.quartz Scheduler Job SimpleScheduleBuilder JobExecutionException JobBuilder TriggerBuilder JobDetail)
           (org.quartz.impl StdSchedulerFactory)
           (org.quartz.spi JobFactory TriggerFiredBundle)
           (java.util TimeZone)))

(defn trigger-builder
  [{:keys [identity description start end priority]}]
  (cond-> (TriggerBuilder/newTrigger)
    (seq identity) (.withIdentity (first identity) (second identity))
    description (.withDescription description)
    start (.startAt start)
    (nil? start) (.startNow)
    end (.endAt end)
    priority (.withPriority (int priority))))

(defn simple-schedule
  [{:keys [interval time-unit repeat misfire]}]
  (let [schedule (SimpleScheduleBuilder/simpleSchedule)]
    (case time-unit
      :millis (.withIntervalInMilliseconds schedule interval)
      :seconds (.withIntervalInSeconds schedule interval)
      :minutes (.withIntervalInMinutes schedule interval)
      :hours (.withIntervalInHours schedule interval)
      nil (.withIntervalInMilliseconds schedule interval))
    (case misfire
      :fire-now (.withMisfireHandlingInstructionFireNow schedule)
      :ignore (.withMisfireHandlingInstructionIgnoreMisfires schedule)
      :next-existing (.withMisfireHandlingInstructionNextWithExistingCount schedule)
      :next-remaining (.withMisfireHandlingInstructionNextWithRemainingCount schedule)
      :now-existing (.withMisfireHandlingInstructionNowWithExistingCount schedule)
      :now-remaining (.withMisfireHandlingInstructionNowWithRemainingCount schedule)
      nil nil)
    (cond
      (number? repeat) (.withRepeatCount schedule repeat)
      (= :forever repeat) (.repeatForever schedule))
    schedule))

(defmulti trigger :type)

(defmethod trigger :simple
  [config]
  (-> (trigger-builder config)
      (.withSchedule (simple-schedule config))
      (.build)))

(defrecord ProxyJob [proxied-job]
  Job
  (execute [_ job-context]
    (try
      (.execute ^Job proxied-job job-context)
      (catch JobExecutionException ex
        (throw ex))
      (catch Exception ex
        (throw (JobExecutionException. ^Exception ex))))))

(defn job-factory
  [scheduled]
  (reify JobFactory
    (newJob [_ bundle _]
      (let [job-detail (.getJobDetail ^TriggerFiredBundle bundle)
            job-key    (.getKey job-detail)]
        (->ProxyJob (get scheduled job-key))))))

(defn proxy
  [job]
  (let [{:keys [identity description recover? durable?]} job]
    (.build (cond-> (JobBuilder/newJob)
              true (.ofType ProxyJob)
              (seq identity) (.withIdentity (first identity) (second identity))
              description (.withDescription description)
              (boolean? recover?) (.requestRecovery recover?)
              (boolean? durable?) (.storeDurably durable?)))))

(defn activate
  [scheduler schedule]
  ;; TODO: potentially improve this loop to carry job->proxy-job map and re-use in the case of job
  ;; TODO: with multiple triggers, currently we create multiple proxy-jobs
  ;; TODO: actually thinking about it we'd need to support that due to re-used jobs with name clashes
  (loop [schedule  schedule
         scheduled {}]
    (if-let [{:keys [job trigger]} (first schedule)]
      (let [proxy-detail (proxy job)]
        (log/info "scheduling" trigger proxy-detail)
        (.scheduleJob scheduler proxy-detail trigger)
        (recur (rest schedule) (assoc scheduled (.getKey ^JobDetail proxy-detail) job)))
      (.setJobFactory scheduler (job-factory scheduled))))
  (.start scheduler)
  scheduler)

(defn initialize
  [config]
  (let [{:keys [schedule time-zone update-check?]} config]
    (log/infof "initializing schedule of [%s] jobs" (count schedule))
    (when time-zone
      (log/infof "with default time-zone %s" time-zone)
      (TimeZone/setDefault (TimeZone/getTimeZone ^String time-zone)))
    (when-not update-check?
      (System/setProperty "org.terracotta.quartz.skipUpdateCheck" "true")
      (log/infof "with quartz update check disabled" time-zone))
    (activate (StdSchedulerFactory/getDefaultScheduler) schedule)))

(defn shutdown
  [scheduler]
  (.shutdown ^Scheduler scheduler))

(defmethod ig/init-key :cronut/trigger
  [_ config]
  (trigger config))

(defmethod ig/init-key :cronut/scheduler
  [_ config]
  (initialize config))

(defmethod ig/halt-key! :cronut/scheduler
  [_ scheduler]
  (shutdown scheduler))

(def data-readers
  {'ig/ref           ig/ref
   'cronut/trigger   troy-west.cronut/trigger})

(defn init-system
  ([config]
   (init-system config nil))
  ([config readers]
   (ig/init (edn/read-string {:readers (merge data-readers readers)} config))))

(defn halt-system
  [system]
  (ig/halt! system))