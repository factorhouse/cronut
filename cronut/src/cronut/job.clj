(ns cronut.job
  (:import (org.quartz DisallowConcurrentExecution Job JobBuilder JobDataMap JobExecutionException)
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
  [job concurrent-execution-disallowed?]
  (let [{:keys [identity description recover? durable? disallow-concurrent-execution?]} job]
    (.build (cond->
              (-> (JobBuilder/newJob (if (or concurrent-execution-disallowed? ;; global concurrency disallowed flag
                                             disallow-concurrent-execution?) ;; job specific concurrency dissalowed flag
                                       SerialProxyJob ProxyJob))
                  (.setJobData (JobDataMap. {"job-impl" job})))
              (seq identity) (.withIdentity (first identity) (second identity))
              description (.withDescription description)
              (boolean? recover?) (.requestRecovery recover?)
              (boolean? durable?) (.storeDurably durable?)))))
