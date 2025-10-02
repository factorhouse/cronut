(ns cronut.job
  (:refer-clojure :exclude [key])
  (:import (org.quartz DisallowConcurrentExecution Job JobBuilder JobDataMap JobDetail JobExecutionException JobKey)
           (org.quartz.spi JobFactory TriggerFiredBundle)))

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

(defn factory
  []
  (reify JobFactory
    (newJob [_ bundle _]
      (let [job-detail (.getJobDetail ^TriggerFiredBundle bundle)
            job-class  (.getJobClass job-detail)
            job-data   (.getJobDataMap job-detail)
            job        (get job-data "job-impl")]
        (if (= job-class SerialProxyJob)
          (->SerialProxyJob job)
          (->ProxyJob job))))))

(defn detail
  ^JobDetail [job opts]
  (let [{:keys [name group description recover? durable? disallow-concurrent-execution?]} opts]
    (.build (cond-> (-> (JobBuilder/newJob (if disallow-concurrent-execution? SerialProxyJob ProxyJob))
                        (.setJobData (JobDataMap. {"job-impl" job})))
              name (.withIdentity name group)
              description (.withDescription description)
              (boolean? recover?) (.requestRecovery recover?)
              (boolean? durable?) (.storeDurably durable?)))))

(defn key
  ([name]
   (key name nil))
  ([name group]
   (JobKey. name group)))