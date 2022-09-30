(ns cronut
  (:refer-clojure :exclude [proxy])
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig])
  (:import (org.quartz Scheduler Job SimpleScheduleBuilder JobExecutionException JobBuilder TriggerBuilder JobDetail CronScheduleBuilder DisallowConcurrentExecution)
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

(defrecord ^{DisallowConcurrentExecution true} SerialProxyJob [proxied-job]
  Job
  (execute [_ job-context]
    (try
      (.execute ^Job proxied-job job-context)
      (catch JobExecutionException ex
        (throw ex))
      (catch Exception ex
        (throw (JobExecutionException. ^Exception ex))))))

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
  [scheduled opts]
  (reify JobFactory
    (newJob [_ bundle _]
      (let [job-detail (.getJobDetail ^TriggerFiredBundle bundle)
            job-key    (.getKey job-detail)]
        (if (:disallowConcurrentExecution? opts)
          (->SerialProxyJob (get scheduled job-key))
          (->ProxyJob (get scheduled job-key)))))))

(defn proxy
  [job opts]
  (let [{:keys [identity description recover? durable?]} job]
    (.build (cond-> (.ofType (JobBuilder/newJob) (if (:disallowConcurrentExecution? opts) SerialProxyJob ProxyJob))
              (seq identity) (.withIdentity (first identity) (second identity))
              description (.withDescription description)
              (boolean? recover?) (.requestRecovery recover?)
              (boolean? durable?) (.storeDurably durable?)))))

(defn activate
  [^Scheduler scheduler schedule global-opts]
  (.clear scheduler)
  (loop [schedule  schedule
         scheduled {}
         proxies   {}]
    (if-let [{:keys [job ^TriggerBuilder trigger]} (first schedule)]
      (if-let [previously-scheduled ^JobDetail (get proxies job)]
        (let [built (.build (.forJob trigger previously-scheduled))]
          (log/info "scheduling new trigger for existing job" built previously-scheduled)
          (.scheduleJob scheduler built)
          (recur (rest schedule) scheduled proxies))
        (let [proxy-detail ^JobDetail (proxy job global-opts)
              job-key      (.getKey proxy-detail)
              built        (.build trigger)]
          (log/info "scheduling new job" built proxy-detail)
          (.scheduleJob scheduler proxy-detail built)
          (recur (rest schedule) (assoc scheduled job-key job) (assoc proxies job proxy-detail))))
      (.setJobFactory scheduler (job-factory scheduled global-opts))))
  (.start scheduler)
  scheduler)

(defn initialize
  [config]
  (let [{:keys [schedule update-check?]} config
        opts (dissoc config :schedule :update-check?)]
    (log/infof "initializing schedule of [%s] jobs" (count schedule))
    (when-not update-check?
      (System/setProperty "org.terracotta.quartz.skipUpdateCheck" "true")
      (log/infof "with quartz update check disabled"))
    (log/infof "with cronut opts %s" opts)
    (activate (StdSchedulerFactory/getDefaultScheduler) schedule config)))

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
  {'cronut/trigger  cronut/trigger-builder
   'cronut/cron     cronut/shortcut-cron
   'cronut/interval cronut/shortcut-interval})