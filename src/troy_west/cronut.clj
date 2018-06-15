(ns troy-west.cronut
  (:refer-clojure :exclude [proxy])
  (:require [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [integrant.core :as ig])
  (:import (org.quartz Scheduler Job SimpleScheduleBuilder JobExecutionException JobBuilder TriggerBuilder JobDetail CronScheduleBuilder)
           (org.quartz.impl StdSchedulerFactory)
           (org.quartz.spi JobFactory TriggerFiredBundle)
           (java.util TimeZone)))

(defn base-trigger-builder
  "Provide a base trigger-builder from configuration"
  [{:keys [identity description start end priority]}]
  (cond-> (TriggerBuilder/newTrigger)
    (seq identity) (.withIdentity (first identity) (second identity))
    description (.withDescription description)
    start (.startAt start)
    (nil? start) (.startNow)
    end (.endAt end)
    priority (.withPriority (int priority))))

(defn simple-schedule
  "Provide a simple schedule from configuration"
  [{:keys [interval time-unit repeat misfire]}]
  (let [schedule (SimpleScheduleBuilder/simpleSchedule)]
    (case time-unit
      :millis (.withIntervalInMilliseconds schedule interval)
      :seconds (.withIntervalInSeconds schedule interval)
      :minutes (.withIntervalInMinutes schedule interval)
      :hours (.withIntervalInHours schedule interval)
      nil (when interval (.withIntervalInMilliseconds schedule interval)))
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

(defn cron-schedule
  "Provide a cron schedule from configuration"
  [{:keys [cron time-zone misfire]}]
  (let [schedule (CronScheduleBuilder/cronSchedule ^String cron)]
    (case misfire
      :ignore (.withMisfireHandlingInstructionIgnoreMisfires schedule)
      :do-nothing (.withMisfireHandlingInstructionDoNothing schedule)
      :fire-and-proceed (.withMisfireHandlingInstructionFireAndProceed schedule)
      nil nil)
    (when time-zone
      (.inTimeZone schedule (TimeZone/getTimeZone ^String time-zone)))
    schedule))

(defmulti trigger-builder :type)

(defmethod trigger-builder :simple
  [config]
  (.withSchedule ^TriggerBuilder (base-trigger-builder config)
                 (simple-schedule config)))

(defmethod trigger-builder :cron
  [config]
  (.withSchedule ^TriggerBuilder (base-trigger-builder config)
                 (cron-schedule config)))

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
    (.build (cond-> (.ofType (JobBuilder/newJob) ProxyJob)
              (seq identity) (.withIdentity (first identity) (second identity))
              description (.withDescription description)
              (boolean? recover?) (.requestRecovery recover?)
              (boolean? durable?) (.storeDurably durable?)))))

(defn activate
  [^Scheduler scheduler schedule]
  (.clear scheduler)
  (loop [schedule  schedule
         scheduled {}
         proxies   {}]
    (if-let [{:keys [job ^TriggerBuilder trigger]} (first schedule)]
      (if-let [previously-scheduled ^JobDetail (get proxies job)]
        (let [built (.build (.forJob trigger previously-scheduled))]
          (log/info "scheduling new trigger for existing job" trigger previously-scheduled)
          (.scheduleJob scheduler built)
          (recur (rest schedule) scheduled proxies))
        (let [proxy-detail ^JobDetail (proxy job)
              job-key      (.getKey proxy-detail)]
          (log/info "scheduling new job" trigger proxy-detail)
          (.scheduleJob scheduler proxy-detail (.build trigger))
          (recur (rest schedule) (assoc scheduled job-key job) (assoc proxies job proxy-detail))))
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

(defn shortcut-interval
  "Trigger immediately, at an interval-ms, run forever (well that's optimistic but you get the idea)"
  [interval-ms]
  (trigger-builder {:type      :simple
                    :interval  interval-ms
                    :time-unit :millis
                    :repeat    :forever}))

(defn shortcut-cron
  [cron]
  (trigger-builder {:type :cron
                    :cron cron}))

(defn shutdown
  [scheduler]
  (.shutdown ^Scheduler scheduler))

(defmethod ig/init-key :cronut/scheduler
  [_ config]
  (initialize config))

(defmethod ig/halt-key! :cronut/scheduler
  [_ scheduler]
  (shutdown scheduler))

(def data-readers
  {'ig/ref          ig/ref
   'cronut/trigger  troy-west.cronut/trigger-builder
   'cronut/cron     troy-west.cronut/shortcut-cron
   'cronut/interval troy-west.cronut/shortcut-interval})

(defn init-system
  "Convenience for starting integrant systems with cronut data-readers"
  ([config]
   (init-system config nil))
  ([config readers]
   (ig/init (edn/read-string {:readers (merge data-readers readers)} config))))

(defn halt-system
  [system]
  (ig/halt! system))