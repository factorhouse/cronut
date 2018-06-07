(ns troy-west.cronut
  (:refer-clojure :exclude [proxy])
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [integrant.core :as ig])
  (:import (java.time.temporal ChronoUnit)
           (org.quartz Scheduler Job SimpleScheduleBuilder JobExecutionException JobBuilder TriggerBuilder)
           (org.quartz.impl StdSchedulerFactory)
           (org.quartz.spi JobFactory TriggerFiredBundle)
           (java.util TimeZone)))

(defn time-unit
  [text]
  ;; Cursive shows a spurious arity highlight below: https://github.com/cursive-ide/cursive/issues/1988
  (ChronoUnit/valueOf (str/upper-case text)))

(defn time-zone
  [text]
  (TimeZone/getTimeZone ^String text))

(defrecord ProxyJob [proxied-job]
  Job
  (execute [_ job-context]
    (try
      (.execute ^Job proxied-job job-context)
      (catch JobExecutionException ex
        (throw ex))
      (catch Exception ex
        (throw (JobExecutionException. ^Exception ex))))))

(defmulti trigger :type)

(defmethod trigger :simple
  [config]
  (-> (TriggerBuilder/newTrigger)
      (.startNow)
      (.withSchedule (-> (SimpleScheduleBuilder/simpleSchedule)
                         (.withIntervalInSeconds 2)
                         (.repeatForever)))
      (.build)))

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
    (.build  (cond-> (JobBuilder/newJob)
               true (.ofType ProxyJob)
               identity (.withIdentity (first identity) (second identity))
               description (.withDescription description)
               (boolean? recover?) (.requestRecovery recover?)
               (boolean? durable?) (.storeDurably durable?)))))

(defn activate
  [scheduler schedule]
  ;; TODO: potentially improve this loop to carry job->proxy-job map and re-use in the case of job
  ;; TODO: with multiple triggers, currently we create multiple proxy-jobs
  (loop [schedule  schedule
         scheduled {}]
    (if-let [{:keys [job trigger]} (first schedule)]
      (let [proxy-job (proxy job)]
        (.scheduleJob scheduler proxy-job trigger)
        (recur (rest schedule) (assoc scheduled (.getKey proxy-job) job)))
      (.setJobFactory scheduler (job-factory scheduled))))
  (.start scheduler)
  scheduler)

(defn initialize
  [config]
  (let [{:keys [schedule time-zone update-check?]} config]
    (log/infof "initializing schedule of [%s] jobs" (count schedule))
    (when time-zone
      (log/infof "with default time-zone %s" time-zone)
      (TimeZone/setDefault time-zone))
    (when-not update-check?
      (System/setProperty "org.terracotta.quartz.skipUpdateCheck" "true")
      (log/infof "with quartz update check disabled" time-zone))
    (activate (StdSchedulerFactory/getDefaultScheduler) schedule)))

(defn shutdown
  [scheduler]
  (.shutdown ^Scheduler scheduler))

(defmethod ig/init-key :cronut/time-unit
  [_ text]
  (time-unit text))

(defmethod ig/init-key :cronut/time-zone
  [_ text]
  (time-zone text))

(defmethod ig/init-key :cronut/scheduler
  [_ config]
  (initialize config))

(defmethod ig/halt-key! :cronut/scheduler
  [_ scheduler]
  (shutdown scheduler))

(def data-readers
  {'ig/ref           ig/ref
   'cronut/time-unit troy-west.cronut/time-unit
   'cronut/time-zone troy-west.cronut/time-zone
   'cronut/trigger   troy-west.cronut/trigger})

(defn init-system
  ([config]
   (init-system config nil))
  ([config readers]
   (ig/init (edn/read-string {:readers (merge data-readers readers)} config))))

(defn halt-system
  [system]
  (ig/halt! system))