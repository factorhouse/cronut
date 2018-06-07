(ns troy-west.cronut
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [integrant.core :as ig])
  (:import (java.time.temporal ChronoUnit)
           (java.time ZoneId)
           (org.quartz Scheduler TriggerBuilder JobBuilder Job SimpleScheduleBuilder JobExecutionException)
           (org.quartz.impl StdSchedulerFactory)
           (org.quartz.spi JobFactory TriggerFiredBundle)))

(def cronut-key "cronut/key")

(defn chrono-unit
  [text]
  ;; If you're using Cursive and see the arity highlight below: https://github.com/cursive-ide/cursive/issues/1988
  (ChronoUnit/valueOf (str/upper-case text)))

(defn zone-id
  [text]
  (ZoneId/of text))

(def utc-zone (ZoneId/of "UTC"))

(defmulti schedule :type)

(defmethod schedule :simple
  [config]
  (-> (SimpleScheduleBuilder/simpleSchedule)
      (.withIntervalInSeconds (:interval config))
      (.repeatForever)))

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
  [triggers-map]
  (reify JobFactory
    (newJob [_ bundle _]
      (let [job-detail (.getJobDetail ^TriggerFiredBundle bundle)
            job-data   (.getJobDataMap job-detail)]
        (->ProxyJob (->> (.get job-data cronut-key)
                         (get triggers-map)
                         :job))))))

(defn initialize
  [config]
  (let [{:keys [update-check? time-zone triggers] :or {time-zone utc-zone}} config]
    (let [triggers-map (->> (map-indexed (fn [idx trigger]
                                           (assert (instance? Job (:job trigger)) "jobs must implement org.quartz.Job")
                                           [(int idx) trigger]) triggers)
                            (into {}))
          scheduler    (StdSchedulerFactory/getDefaultScheduler)]
      (log/infof "initializing [%s] triggers in %s" (count triggers) time-zone)
      (when-not update-check?
        (System/setProperty "org.terracotta.quartz.skipUpdateCheck" "true")
        (log/infof "quartz update check disabled" time-zone))
      (.setJobFactory scheduler (job-factory triggers-map))
      (doseq [[idx trigger] triggers-map]
        (.scheduleJob scheduler
                      (-> (JobBuilder/newJob)
                          (.ofType ProxyJob)
                          (.usingJobData ^String cronut-key ^Integer idx)
                          (.build))
                      (-> (TriggerBuilder/newTrigger)
                          (.startNow)
                          (.withSchedule (:schedule trigger))
                          (.build))))
      (.start scheduler)
      scheduler)))

(defn shutdown
  [scheduler]
  (.shutdown ^Scheduler scheduler))
(defmethod ig/init-key :cronut/time-unit
  [_ text]
  (chrono-unit text))

(defmethod ig/init-key :cronut/time-zone
  [_ text]
  (zone-id text))

(defmethod ig/init-key :cronut/scheduler
  [_ config]
  (initialize config))

(defmethod ig/halt-key! :cronut/scheduler
  [_ scheduler]
  (shutdown scheduler))

(def data-readers
  {'ig/ref           ig/ref
   'cronut/time-unit troy-west.cronut/chrono-unit
   'cronut/time-zone troy-west.cronut/zone-id
   'cronut/schedule  troy-west.cronut/schedule})

(defn init
  [config]
  (ig/init (edn/read-string {:readers data-readers} config)))

(defn halt!
  [system]
  (ig/halt! system))