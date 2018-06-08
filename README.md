# Cronut: Scheduled Execution via Quartz and Integrant

> "Cronut is a good name, you can call it that if you want to" - James Sofra

Cronut provides a data-first Clojure wrapper for the [Quartz Job Scheduler](http://www.quartz-scheduler.org/)

[![Clojars Project](https://img.shields.io/clojars/v/com.troy-west/cronut.svg)](https://clojars.org/com.troy-west/cronut)

#Summary

Quartz is richly featured, open source jobn scheduling library that is fairly standard on JVM projects.

Clojure has two wrappers for Quartz already:

1. [Quartzite](https://github.com/michaelklishin/quartzite) by Michael Klishin / ClojureWerkz
2. [Twarc](https://github.com/prepor/twarc) by Andrew Rudenko / Rudenko

There are also a number of non-quartz based schedulers, so why another?

Cronut differs from Quartzite / Twarc in that:

1. Configured entire from data
2. No macros or new protocols, just implement the org.quartz.Job interface
3. No global state
4. Latest version of Quartz
5. Shortcut data-readers for common use-cases
6. Easily extensible for further triggers
7. Bindings provided for [Integrant](https://github.com/weavejester/integrant)

#Usage

Given an Integrant System 

````clojure
{:dep/one          {:a 1}

 :test.job/one     {:dep-one #ig/ref :dep/one}

 :test.job/two     {:identity    ["job-two" "test"]
                    :description "test job"
                    :recover?    true
                    :durable?    false
                    :dep-one     #ig/ref :dep/one
                    :dep-two     #ig/ref :test.job/one}

 :cronut/scheduler {:time-zone "Australia/Melbourne"
                    :schedule  [;; basic interval
                                {:job     #ig/ref :test.job/one
                                 :trigger #cronut/trigger {:type      :simple
                                                           :interval  2
                                                           :time-unit :seconds
                                                           :repeat    :forever}}

                                ;; full interval
                                {:job     #ig/ref :test.job/two
                                 :trigger #cronut/trigger {:type        :simple
                                                           :interval    3000
                                                           :repeat      :forever
                                                           :identity    ["trigger-two" "test"]
                                                           :description "test trigger"
                                                           :start       #inst "2019-01-01T00:00:00.000-00:00"
                                                           :end         #inst "2019-02-01T00:00:00.000-00:00"
                                                           :priority    5}}

                                ;; shortcut interval
                                {:job     #ig/ref :test.job/two
                                 :trigger #cronut/interval 3500}

                                ;; basic cron
                                {:job     #ig/ref :test.job/two
                                 :trigger #cronut/trigger {:type :cron
                                                           :cron "*/4 * * * * ?"}}

                                ;; full cron
                                {:job     #ig/ref :test.job/two
                                 :trigger #cronut/trigger {:type        :cron
                                                           :cron        "*/6 * * * * ?"
                                                           :identity    ["trigger-five" "test"]
                                                           :description "another-test trigger"
                                                           :start       #inst "2018-01-01T00:00:00.000-00:00"
                                                           :end         #inst "2029-02-01T00:00:00.000-00:00"
                                                           :priority    4}}

                                ;; shortcut cron
                                {:job     #ig/ref :test.job/two
                                 :trigger #cronut/cron "*/8 * * * * ?"}]}}

````

And the associated lifecycle implementation

````clojure
(defrecord TestDefrecordJobImpl [identity description recover? durable? test-dep]
  Job
  (execute [this job-context]
    (log/info "Defrecord Impl:" this)))

(defmethod ig/init-key :dep/one
  [_ config]
  config)

(defmethod ig/init-key :test.job/one
  [_ config]
  (reify Job
    (execute [this job-context]
      (log/info "Reified Impl:" config))))

(defmethod ig/init-key :test.job/two
  [_ config]
  (map->TestDefrecordJobImpl config))
````

We can stop and start / stop a system that runs org.quartz.Job instances per our schedule

````clojure
(require '[troy-west.cronut.integration-fixture :as itf])
=> nil

(itf/initialize!)
16:48:49.633 INFO  [nREPL-worker-0] troy-west.cronut – initializing schedule of [6] jobs
16:48:49.635 INFO  [nREPL-worker-0] troy-west.cronut – with default time-zone Australia/Melbourne
16:48:49.635 INFO  [nREPL-worker-0] troy-west.cronut – with quartz update check disabled
16:48:49.663 INFO  [nREPL-worker-0] org.quartz.impl.StdSchedulerFactory – Using default implementation for ThreadExecutor
16:48:49.665 INFO  [nREPL-worker-0] org.quartz.simpl.SimpleThreadPool – Job execution threads will use class loader of thread: nREPL-worker-0
16:48:49.674 INFO  [nREPL-worker-0] o.quartz.core.SchedulerSignalerImpl – Initialized Scheduler Signaller of type: class org.quartz.core.SchedulerSignalerImpl
16:48:49.674 INFO  [nREPL-worker-0] org.quartz.core.QuartzScheduler – Quartz Scheduler v.2.3.0 created.
16:48:49.675 INFO  [nREPL-worker-0] org.quartz.simpl.RAMJobStore – RAMJobStore initialized.
16:48:49.676 INFO  [nREPL-worker-0] org.quartz.core.QuartzScheduler – Scheduler meta-data: Quartz Scheduler (v2.3.0) 'DefaultQuartzScheduler' with instanceId 'NON_CLUSTERED'
  Scheduler class: 'org.quartz.core.QuartzScheduler' - running locally.
  NOT STARTED.
  Currently in standby mode.
  Number of jobs executed: 0
  Using thread pool 'org.quartz.simpl.SimpleThreadPool' - with 10 threads.
  Using job-store 'org.quartz.simpl.RAMJobStore' - which does not support persistence. and is not clustered.

16:48:49.676 INFO  [nREPL-worker-0] org.quartz.impl.StdSchedulerFactory – Quartz scheduler 'DefaultQuartzScheduler' initialized from default resource file in Quartz package: 'quartz.properties'
16:48:49.676 INFO  [nREPL-worker-0] org.quartz.impl.StdSchedulerFactory – Quartz scheduler version: 2.3.0
16:48:49.679 INFO  [nREPL-worker-0] troy-west.cronut – scheduling new job #object[org.quartz.TriggerBuilder 0x178b9e44 org.quartz.TriggerBuilder@178b9e44] #object[org.quartz.impl.JobDetailImpl 0x211c9561 JobDetail 'DEFAULT.6da64b5bd2ee-26287b29-b807-482d-bc49-3505c846f3c7':  jobClass: 'troy_west.cronut.ProxyJob concurrentExectionDisallowed: false persistJobDataAfterExecution: false isDurable: false requestsRecovers: false]
16:48:49.683 INFO  [nREPL-worker-0] troy-west.cronut – scheduling new job #object[org.quartz.TriggerBuilder 0x6dde57f4 org.quartz.TriggerBuilder@6dde57f4] #object[org.quartz.impl.JobDetailImpl 0x705063d2 JobDetail 'test.job-two':  jobClass: 'troy_west.cronut.ProxyJob concurrentExectionDisallowed: false persistJobDataAfterExecution: false isDurable: false requestsRecovers: true]
16:48:49.684 INFO  [nREPL-worker-0] troy-west.cronut – scheduling new trigger for existing job #object[org.quartz.TriggerBuilder 0x3bfd0733 org.quartz.TriggerBuilder@3bfd0733] #object[org.quartz.impl.JobDetailImpl 0x705063d2 JobDetail 'test.job-two':  jobClass: 'troy_west.cronut.ProxyJob concurrentExectionDisallowed: false persistJobDataAfterExecution: false isDurable: false requestsRecovers: true]
16:48:49.687 INFO  [nREPL-worker-0] troy-west.cronut – scheduling new trigger for existing job #object[org.quartz.TriggerBuilder 0x18e3102c org.quartz.TriggerBuilder@18e3102c] #object[org.quartz.impl.JobDetailImpl 0x705063d2 JobDetail 'test.job-two':  jobClass: 'troy_west.cronut.ProxyJob concurrentExectionDisallowed: false persistJobDataAfterExecution: false isDurable: false requestsRecovers: true]
16:48:49.689 INFO  [nREPL-worker-0] troy-west.cronut – scheduling new trigger for existing job #object[org.quartz.TriggerBuilder 0x39c8f5df org.quartz.TriggerBuilder@39c8f5df] #object[org.quartz.impl.JobDetailImpl 0x705063d2 JobDetail 'test.job-two':  jobClass: 'troy_west.cronut.ProxyJob concurrentExectionDisallowed: false persistJobDataAfterExecution: false isDurable: false requestsRecovers: true]
16:48:49.690 INFO  [nREPL-worker-0] troy-west.cronut – scheduling new trigger for existing job #object[org.quartz.TriggerBuilder 0x6dba1fe org.quartz.TriggerBuilder@6dba1fe] #object[org.quartz.impl.JobDetailImpl 0x705063d2 JobDetail 'test.job-two':  jobClass: 'troy_west.cronut.ProxyJob concurrentExectionDisallowed: false persistJobDataAfterExecution: false isDurable: false requestsRecovers: true]
16:48:49.690 INFO  [nREPL-worker-0] org.quartz.core.QuartzScheduler – JobFactory set to: troy_west.cronut$job_factory$reify__2264@50ea0218
16:48:49.690 INFO  [nREPL-worker-0] org.quartz.core.QuartzScheduler – Scheduler DefaultQuartzScheduler_$_NON_CLUSTERED started.
=>
{:dep/one {:a 1},
 :test.job/one #object[troy_west.cronut.integration_fixture$eval2343$fn$reify__2345
                       0x2e906b8a
                       "troy_west.cronut.integration_fixture$eval2343$fn$reify__2345@2e906b8a"],
 :test.job/two #troy_west.cronut.integration_fixture.TestDefrecordJobImpl{:identity ["job-two" "test"],
                                                                          :description "test job",
                                                                          :recover? true,
                                                                          :durable? false,
                                                                          :test-dep nil,
                                                                          :dep-one {:a 1},
                                                                          :dep-two #object[troy_west.cronut.integration_fixture$eval2343$fn$reify__2345
                                                                                           0x2e906b8a
                                                                                           "troy_west.cronut.integration_fixture$eval2343$fn$reify__2345@2e906b8a"]},
 :cronut/scheduler #object[org.quartz.impl.StdScheduler 0x7565dd8e "org.quartz.impl.StdScheduler@7565dd8e"]}
 16:48:49.697 INFO  [DefaultQuartzScheduler_Worker-1] troy-west.cronut.integration-fixture – Reified Impl: {:dep-one {:a 1}}
 16:48:49.697 INFO  [DefaultQuartzScheduler_Worker-2] troy-west.cronut.integration-fixture – Defrecord Impl: #troy_west.cronut.integration_fixture.TestDefrecordJobImpl{:identity [job-two test], :description test job, :recover? true, :durable? false, :test-dep nil, :dep-one {:a 1}, :dep-two #object[troy_west.cronut.integration_fixture$eval2343$fn$reify__2345 0x2e906b8a troy_west.cronut.integration_fixture$eval2343$fn$reify__2345@2e906b8a]}
 16:48:49.699 INFO  [DefaultQuartzScheduler_Worker-3] troy-west.cronut.integration-fixture – Defrecord Impl: #troy_west.cronut.integration_fixture.TestDefrecordJobImpl{:identity [job-two test], :description test job, :recover? true, :durable? false, :test-dep nil, :dep-one {:a 1}, :dep-two #object[troy_west.cronut.integration_fixture$eval2343$fn$reify__2345 0x2e906b8a troy_west.cronut.integration_fixture$eval2343$fn$reify__2345@2e906b8a]}
 16:48:51.495 INFO  [DefaultQuartzScheduler_Worker-4] troy-west.cronut.integration-fixture – Reified Impl: {:dep-one {:a 1}}
 16:48:52.004 INFO  [DefaultQuartzScheduler_Worker-5] troy-west.cronut.integration-fixture – Defrecord Impl: #troy_west.cronut.integration_fixture.TestDefrecordJobImpl{:identity [job-two test], :description test job, :recover? true, :durable? false, :test-dep nil, :dep-one {:a 1}, :dep-two #object[troy_west.cronut.integration_fixture$eval2343$fn$reify__2345 0x2e906b8a troy_west.cronut.integration_fixture$eval2343$fn$reify__2345@2e906b8a]}
(itf/shutdown!)
=> nil 
16:48:52.495 INFO  [nREPL-worker-0] org.quartz.core.QuartzScheduler – Scheduler DefaultQuartzScheduler_$_NON_CLUSTERED shutting down.
16:48:52.495 INFO  [nREPL-worker-0] org.quartz.core.QuartzScheduler – Scheduler DefaultQuartzScheduler_$_NON_CLUSTERED paused.

16:48:52.496 INFO  [nREPL-worker-0] org.quartz.core.QuartzScheduler – Scheduler DefaultQuartzScheduler_$_NON_CLUSTERED shutdown complete.
````