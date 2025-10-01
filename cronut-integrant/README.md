# Cronut-Integrant: Integrant bindings for Cronut

[![Cronut Test](https://github.com/factorhouse/cronut/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/factorhouse/cronut/actions/workflows/ci.yml)
[![Clojars Project](https://img.shields.io/clojars/v/io.factorhouse/cronut-integrant.svg)](https://clojars.org/io.factorhouse/cronut-integrant)

# Summary

Cronut-Integrant provides bindings for [Cronut](https://github.com/factorhouse/cronut)
to [Integrant](https://github.com/weavejester/integrant), the DI micro-framework.

## Related Projects

| Project                                                     | Desription                                                                                                   | Clojars Project                                                                                                                         |
|-------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| [cronut](https://github.com/factorhouse/cronut)             | Cronut with [Jakarta](https://en.wikipedia.org/wiki/Jakarta_EE) support (Primary)                            | [![Clojars Project](https://img.shields.io/clojars/v/io.factorhouse/cronut-javax.svg)](https://clojars.org/io.factorhouse/cronut)       |
| [cronut-javax](https://github.com/factorhouse/cronut-javax) | Cronut with [Javax](https://jakarta.ee/blogs/javax-jakartaee-namespace-ecosystem-progress/) support (Legacy) | [![Clojars Project](https://img.shields.io/clojars/v/io.factorhouse/cronut-javax.svg)](https://clojars.org/io.factorhouse/cronut-javax) |

# Contents

- [Configuration](#configuration)
    * [`:cronut/scheduler` definition](#cronutscheduler-definition)
        + [Scheduler example](#scheduler-example)
    * [`:job` definition](#job-definition)
        + [Job example](#job-example)
    * [`:trigger` definition](#trigger-definition)
        + [`:trigger` tagged literals](#trigger-tagged-literals)
            - [`#cronut/cron`: Simple Cron Scheduling](#cronutcron-simple-cron-scheduling)
            - [`#cronut/interval`: Simple Interval Scheduling](#cronutinterval-simple-interval-scheduling)
            - [`#cronut/trigger`: Full trigger definition](#cronuttrigger-full-trigger-definition)
    * [Concurrent execution](#concurrent-execution)
        + [Global concurrent execution](#global-concurrent-execution)
        + [Job-specific concurrent execution](#job-specific-concurrent-execution)
        + [Misfire configuration](#misfire-configuration)
- [System initialization](#system-initialization)
- [Example system](#example-system)
    * [Configuration](#configuration-1)
    * [Job definitions](#job-definitions)
    * [Helper functions](#helper-functions)
    * [Putting it together](#putting-it-together)
        + [Starting the system](#starting-the-system)
        + [Logs of the running system](#logs-of-the-running-system)
        + [Stopping the system](#stopping-the-system)
- [License](#license)

# Configuration

A quartz `scheduler` runs a `job` on a schedule defined by a `trigger`.

## `:cronut/scheduler` definition

Cronut provides access to the Quartz Scheduler, exposed via Integrant with `:cronut/scheduler`

The scheduler supports the following fields:

1. `:schedule`: (required) - a sequence of 'items' to schedule, each being a map containing a :job and :trigger
2. `:concurrent-execution-disallowed?`: (optional, default false) - run all jobs with @DisableConcurrentExecution
3. `:update-check?`: (optional, default false) - check for Quartz updates on system startup

### Scheduler example

````clojure
:cronut/scheduler {:schedule                         [{:job     #ig/ref :test.job/two
                                                       :trigger #cronut/interval 3500}
                                                      {:job     #ig/ref :test.job/two
                                                       :trigger #cronut/cron "*/8 * * * * ?"
                                                       :misfire :do-nothing}]
                   :concurrent-execution-disallowed? true}
````

## `:job` definition

The `:job` in every scheduled item must implement the org.quartz.Job interface

The expectation being that every 'job' in your Integrant system will reify that interface, either directly via `reify`
or by returning a `defrecord` that implements the interface. e.g.

````clojure
(defmethod ig/init-key :test.job/one
  [_ config]
  (reify Job
    (execute [this job-context]
      (log/info "Reified Impl:" config))))

(defrecord TestDefrecordJobImpl [identity description recover? durable? test-dep]
  Job
  (execute [this job-context]
    (log/info "Defrecord Impl:" this)))

(defmethod ig/init-key :test.job/two
  [_ config]
  (map->TestDefrecordJobImpl config))
````

Cronut supports further Quartz configuration of jobs (identity, description, recovery, and durability) by expecting
those values to be assoc'd onto your job. You do not have to set them (in fact in most cases you can likely ignore
them), however if you do want that control you will likely use the `defrecord` approach as opposed to `reify`.

Concurrent execution can be controlled on a per-job bases with the `disallow-concurrent-execution?` flag.

### Job example

````clojure
:test.job/two {:identity                       ["job-two" "test"]
               :description                    "test job"
               :recover?                       true
               :durable?                       false
               :disallow-concurrent-execution? true
               :dep-one                        #ig/ref :dep/one
               :dep-two                        #ig/ref :test.job/one}
````                    

## `:trigger` definition

The `:trigger` in every scheduled item must resolve to an org.quartz.Trigger of some variety or another, to ease that
resolution Cronut provides the following tagged literals:

### `:trigger` tagged literals

#### `#cronut/cron`: Simple Cron Scheduling

A job is scheduled to run on a cron by using the `#cronut/cron` tagged literal followed by a valid cron expression

The job will start immediately when the system is initialized, and runs in the default system time-zone

````clojure
:trigger #cronut/cron "*/8 * * * * ?"
````

#### `#cronut/interval`: Simple Interval Scheduling

A job is scheduled to run periodically by using the `#cronut/interval` tagged literal followed by a milliseconds value

````clojure
:trigger #cronut/interval 3500
````

#### `#cronut/trigger`: Full trigger definition

Both `#cronut/cron` and `#cronut/interval` are effectively shortcuts to full trigger definition with sensible defaults.

The `#cronut/trigger` tagged literal supports the full set of Quartz configuration triggers:

````clojure
;; interval
:trigger #cronut/trigger {:type        :simple
                          :interval    3000
                          :repeat      :forever
                          :identity    ["trigger-two" "test"]
                          :description "sample simple trigger"
                          :start       #inst "2019-01-01T00:00:00.000-00:00"
                          :end         #inst "2019-02-01T00:00:00.000-00:00"
                          :misfire     :ignore
                          :priority    5}

;;cron
:trigger #cronut/trigger {:type        :cron
                          :cron        "*/6 * * * * ?"
                          :identity    ["trigger-five" "test"]
                          :description "sample cron trigger"
                          :start       #inst "2018-01-01T00:00:00.000-00:00"
                          :end         #inst "2029-02-01T00:00:00.000-00:00"
                          :time-zone   "Australia/Melbourne"
                          :misfire     :fire-and-proceed
                          :priority    4}
````

## Concurrent execution

### Global concurrent execution

Set `:concurrent-execution-disallowed?` on the scheduler to disable concurrent execution of all jobs.

### Job-specific concurrent execution

Set `:disallow-concurrent-execution?` on a specific job to disable concurrent execution of that job only.

### Misfire configuration

If you disable concurrent job execution ensure you understand Quartz Misfire options and remember to set
`org.quartz.jobStore.misfireThreshold=[some ms value]` in your quartz.properties file. See Quartz documentation for more
information.

See our test-resources/config.edn and test-resources/org/quartz/quartz.properties for examples of misfire threshold and
behaviour configuration.

# System initialization

When initializing an Integrant system you will need to provide the Cronut data readers.

See: `cronut/data-readers` for convenience.

````clojure
(def data-readers
  {'cronut/trigger  cronut/trigger-builder
   'cronut/cron     cronut/shortcut-cron
   'cronut/interval cronut/shortcut-interval})
````

e.g.

````clojure
(defn init-system
  "Convenience for starting integrant systems with cronut data-readers"
  ([config]
   (init-system config nil))
  ([config readers]
   (ig/init (ig/read-string {:readers (merge cronut/data-readers readers)} config))))
````

# Example system

This repository contains an example system composed of of integratant configuration, job definitions, and helper
functions.

## Configuration

Integrant configuration source: [dev-resources/config.edn](dev-resources/config.edn).

````clojure
{:dep/one          {:a 1}

 :test.job/one     {:dep-one #ig/ref :dep/one}

 :test.job/two     {:identity    ["name1" "group2"]
                    :description "test job"
                    :recover?    true
                    :durable?    false
                    :dep-one     #ig/ref :dep/one
                    :dep-two     #ig/ref :test.job/one}

 :test.job/three   {}

 :cronut/scheduler {:update-check?                    false
                    :concurrent-execution-disallowed? true
                    :schedule                         [;; basic interval
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
                                                                                  :time-zone   "Australia/Melbourne"
                                                                                  :priority    4}}

                                                       ;; shortcut cron
                                                       {:job     #ig/ref :test.job/two
                                                        :trigger #cronut/cron "*/8 * * * * ?"}

                                                       ;; Note: This job misfires because it takes 7 seconds to run, but runs every 5 seconds, and isn't allowed to run concurrently with {:disallowConcurrentExecution? true}
                                                       ;;       So every second job fails to run, and is just ignored with the :do-nothing :misfire rule
                                                       {:job     #ig/ref :test.job/three
                                                        :trigger #cronut/trigger {:type    :cron
                                                                                  :cron    "*/5 * * * * ?"
                                                                                  :misfire :do-nothing}}]}}

````

## Job definitions

Job definitions source: [test/cronut/integration-test.clj](test/cronut/integration_test.clj)

```clojure
(defrecord TestDefrecordJobImpl [identity description recover? durable? test-dep disallow-concurrent-execution?]
  Job
  (execute [this _job-context]
    (log/info "Defrecord Impl:" this)))

(defmethod ig/init-key :dep/one
  [_ config]
  config)

(defmethod ig/init-key :test.job/one
  [_ config]
  (reify Job
    (execute [_this _job-context]
      (log/info "Reified Impl:" config))))

(defmethod ig/init-key :test.job/two
  [_ config]
  (map->TestDefrecordJobImpl config))

(defmethod ig/init-key :test.job/three
  [_ config]
  (reify Job
    (execute [_this _job-context]
      (let [rand-id (str (UUID/randomUUID))]
        (log/info rand-id "Reified Impl (Job Delay 7s):" config)
        (async/<!! (async/timeout 7000))
        (log/info rand-id "Finished")))))
```

## Helper functions

Helper functions source: [test/cronut/integration-test.clj](test/cronut/integration_test.clj)

````clojure
(defn init-system
  "Example of starting integrant cronut systems with data-readers"
  ([]
   (init-system (slurp (io/resource "config.edn"))))
  ([config]
   (init-system config nil))
  ([config readers]
   (ig/init (ig/read-string {:readers (merge cig/data-readers readers)} config))))

(defn halt-system
  "Example of stopping integrant cronut systems"
  [system]
  (ig/halt! system))
````

## Putting it together

### Starting the system

```clojure
(do
  (require '[cronut.integration-test :as test])
  (test/init-system))
```

### Logs of the running system

```bash
16:29:37.378 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] cronut – initializing scheduler
16:29:37.378 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] cronut – with quartz update check disabled
16:29:37.387 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] org.quartz.impl.StdSchedulerFactory – Using default implementation for ThreadExecutor
16:29:37.392 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] o.quartz.core.SchedulerSignalerImpl – Initialized Scheduler Signaller of type: class org.quartz.core.SchedulerSignalerImpl
16:29:37.392 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] org.quartz.core.QuartzScheduler – Quartz Scheduler v2.5.0 created.
16:29:37.393 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] org.quartz.simpl.RAMJobStore – RAMJobStore initialized.
16:29:37.393 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] org.quartz.core.QuartzScheduler – Scheduler meta-data: Quartz Scheduler (v2.5.0) 'CronutScheduler' with instanceId 'NON_CLUSTERED'
  Scheduler class: 'org.quartz.core.QuartzScheduler' - running locally.
  NOT STARTED.
  Currently in standby mode.
  Number of jobs executed: 0
  Using thread pool 'org.quartz.simpl.SimpleThreadPool' - with 6 threads.
  Using job-store 'org.quartz.simpl.RAMJobStore' - which does not support persistence. and is not clustered.

16:29:37.393 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] org.quartz.impl.StdSchedulerFactory – Quartz scheduler 'CronutScheduler' initialized from default resource file in Quartz package: 'quartz.properties'
16:29:37.393 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] org.quartz.impl.StdSchedulerFactory – Quartz scheduler version: 2.5.0
16:29:37.393 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] cronut – with global concurrent execution disallowed
16:29:37.393 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] org.quartz.core.QuartzScheduler – JobFactory set to: cronut.job$factory$reify__12146@101e15ee
16:29:37.393 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] cronut – scheduling [7] jobs
16:29:37.401 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] cronut – scheduling new job #object[org.quartz.impl.triggers.SimpleTriggerImpl 0x467fb22c Trigger 'DEFAULT.6da64b5bd2ee-880e78bc-4873-42b3-9b5a-64bcfce6f5a1':  triggerClass: 'org.quartz.impl.triggers.SimpleTriggerImpl calendar: 'null' misfireInstruction: 0 nextFireTime: null] #object[org.quartz.impl.JobDetailImpl 0x28117587 JobDetail 'DEFAULT.6da64b5bd2ee-b8c8c92a-b907-4859-b28a-6be8f4f41fea':  jobClass: 'cronut.job.SerialProxyJob concurrentExecutionDisallowed: true persistJobDataAfterExecution: false isDurable: false requestsRecovers: false]
16:29:37.402 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] cronut – scheduling new job #object[org.quartz.impl.triggers.SimpleTriggerImpl 0x3cd7003b Trigger 'test.trigger-two':  triggerClass: 'org.quartz.impl.triggers.SimpleTriggerImpl calendar: 'null' misfireInstruction: 0 nextFireTime: null] #object[org.quartz.impl.JobDetailImpl 0x737df17e JobDetail 'test-name.test-group':  jobClass: 'cronut.job.SerialProxyJob concurrentExecutionDisallowed: true persistJobDataAfterExecution: false isDurable: false requestsRecovers: true]
16:29:37.402 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] cronut – scheduling new trigger for existing job #object[org.quartz.impl.triggers.SimpleTriggerImpl 0x41c1388d Trigger 'DEFAULT.6da64b5bd2ee-c94286ad-ab6a-4539-92db-4bcf467f77fd':  triggerClass: 'org.quartz.impl.triggers.SimpleTriggerImpl calendar: 'null' misfireInstruction: 0 nextFireTime: null] #object[org.quartz.impl.JobDetailImpl 0x1e1c028d JobDetail 'test-name.test-group':  jobClass: 'cronut.job.SerialProxyJob concurrentExecutionDisallowed: true persistJobDataAfterExecution: false isDurable: false requestsRecovers: true]
16:29:37.403 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] cronut – scheduling new trigger for existing job #object[org.quartz.impl.triggers.CronTriggerImpl 0x78817b4e Trigger 'DEFAULT.6da64b5bd2ee-f706f964-b8c2-4d55-b7a5-a9a4c5795f4f':  triggerClass: 'org.quartz.impl.triggers.CronTriggerImpl calendar: 'null' misfireInstruction: 0 nextFireTime: null] #object[org.quartz.impl.JobDetailImpl 0x36fdd3e3 JobDetail 'test-name.test-group':  jobClass: 'cronut.job.SerialProxyJob concurrentExecutionDisallowed: true persistJobDataAfterExecution: false isDurable: false requestsRecovers: true]
16:29:37.404 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] cronut – scheduling new trigger for existing job #object[org.quartz.impl.triggers.CronTriggerImpl 0x44d4758e Trigger 'test.trigger-five':  triggerClass: 'org.quartz.impl.triggers.CronTriggerImpl calendar: 'null' misfireInstruction: 0 nextFireTime: null] #object[org.quartz.impl.JobDetailImpl 0x6ee1851e JobDetail 'test-name.test-group':  jobClass: 'cronut.job.SerialProxyJob concurrentExecutionDisallowed: true persistJobDataAfterExecution: false isDurable: false requestsRecovers: true]
16:29:37.404 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] cronut – scheduling new trigger for existing job #object[org.quartz.impl.triggers.CronTriggerImpl 0x4900a5b0 Trigger 'DEFAULT.6da64b5bd2ee-31aa5433-c5d3-4ddd-b21e-04a2d4920548':  triggerClass: 'org.quartz.impl.triggers.CronTriggerImpl calendar: 'null' misfireInstruction: 0 nextFireTime: null] #object[org.quartz.impl.JobDetailImpl 0xc979851 JobDetail 'test-name.test-group':  jobClass: 'cronut.job.SerialProxyJob concurrentExecutionDisallowed: true persistJobDataAfterExecution: false isDurable: false requestsRecovers: true]
16:29:37.404 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] cronut – scheduling new job #object[org.quartz.impl.triggers.CronTriggerImpl 0x6b88f448 Trigger 'DEFAULT.6da64b5bd2ee-6fd7a0a0-bf32-4256-8c27-a2b29f11d5ef':  triggerClass: 'org.quartz.impl.triggers.CronTriggerImpl calendar: 'null' misfireInstruction: 2 nextFireTime: null] #object[org.quartz.impl.JobDetailImpl 0x9a35552 JobDetail 'DEFAULT.6da64b5bd2ee-c6c26ace-ce1a-44ed-b922-096a3f5233f4':  jobClass: 'cronut.job.SerialProxyJob concurrentExecutionDisallowed: true persistJobDataAfterExecution: false isDurable: false requestsRecovers: false]
16:29:37.405 INFO  [nREPL-session-03644f18-045b-47e8-b0be-5c9b069c6ee0] org.quartz.core.QuartzScheduler – Scheduler CronutScheduler_$_NON_CLUSTERED started.
16:29:37.406 INFO  [CronutScheduler_Worker-1] cronut.integration-test – Reified Impl: {:dep-one {:a 1}}
16:29:37.408 INFO  [CronutScheduler_Worker-2] cronut.integration-test – Defrecord Impl: #cronut.integration_test.TestDefrecordJobImpl{:identity [test-group test-name], :description test job, :recover? true, :durable? false, :test-dep nil, :disallow-concurrent-execution? nil, :dep-one {:a 1}, :dep-two #object[cronut.integration_test$eval13104$fn$reify__13106 0x45425cf3 cronut.integration_test$eval13104$fn$reify__13106@45425cf3]}
16:29:37.409 INFO  [CronutScheduler_Worker-3] cronut.integration-test – Defrecord Impl: #cronut.integration_test.TestDefrecordJobImpl{:identity [test-group test-name], :description test job, :recover? true, :durable? false, :test-dep nil, :disallow-concurrent-execution? nil, :dep-one {:a 1}, :dep-two #object[cronut.integration_test$eval13104$fn$reify__13106 0x45425cf3 cronut.integration_test$eval13104$fn$reify__13106@45425cf3]}
=>
{:dep/one {:a 1},
 :test.job/one #object[cronut.integration_test$eval13104$fn$reify__13106
                       0x45425cf3
                       "cronut.integration_test$eval13104$fn$reify__13106@45425cf3"],
 :test.job/three #object[cronut.integration_test$eval13115$fn$reify__13117
                         0x7527011a
                         "cronut.integration_test$eval13115$fn$reify__13117@7527011a"],
 :test.job/two #cronut.integration_test.TestDefrecordJobImpl{:identity ["test-group" "test-name"],
                                                             :description "test job",
                                                             :recover? true,
                                                             :durable? false,
                                                             :test-dep nil,
                                                             :disallow-concurrent-execution? nil,
                                                             :dep-one {:a 1},
                                                             :dep-two #object[cronut.integration_test$eval13104$fn$reify__13106
                                                                              0x45425cf3
                                                                              "cronut.integration_test$eval13104$fn$reify__13106@45425cf3"]},
 :cronut/scheduler #object[org.quartz.impl.StdScheduler 0x59a18142 "org.quartz.impl.StdScheduler@59a18142"]}
16:29:39.368 INFO  [CronutScheduler_Worker-4] cronut.integration-test – Reified Impl: {:dep-one {:a 1}}
16:29:40.005 INFO  [CronutScheduler_Worker-5] cronut.integration-test – Defrecord Impl: #cronut.integration_test.TestDefrecordJobImpl{:identity [test-group test-name], :description test job, :recover? true, :durable? false, :test-dep nil, :disallow-concurrent-execution? nil, :dep-one {:a 1}, :dep-two #object[cronut.integration_test$eval13104$fn$reify__13106 0x45425cf3 cronut.integration_test$eval13104$fn$reify__13106@45425cf3]}
16:29:40.005 INFO  [CronutScheduler_Worker-6] cronut.integration-test – 3979b197-5683-47a9-a267-dcaded343697 Reified Impl (Job Delay 7s): {}
16:29:40.006 INFO  [CronutScheduler_Worker-1] cronut.integration-test – Defrecord Impl: #cronut.integration_test.TestDefrecordJobImpl{:identity [test-group test-name], :description test job, :recover? true, :durable? false, :test-dep nil, :disallow-concurrent-execution? nil, :dep-one {:a 1}, :dep-two #object[cronut.integration_test$eval13104$fn$reify__13106 0x45425cf3 cronut.integration_test$eval13104$fn$reify__13106@45425cf3]}
16:29:40.876 INFO  [CronutScheduler_Worker-2] cronut.integration-test – Defrecord Impl: #cronut.integration_test.TestDefrecordJobImpl{:identity [test-group test-name], :description test job, :recover? true, :durable? false, :test-dep nil, :disallow-concurrent-execution? nil, :dep-one {:a 1}, :dep-two #object[cronut.integration_test$eval13104$fn$reify__13106 0x45425cf3 cronut.integration_test$eval13104$fn$reify__13106@45425cf3]}
16:29:41.368 INFO  [CronutScheduler_Worker-3] cronut.integration-test – Reified Impl: {:dep-one {:a 1}}
16:29:42.004 INFO  [CronutScheduler_Worker-4] cronut.integration-test – Defrecord Impl: #cronut.integration_test.TestDefrecordJobImpl{:identity [test-group test-name], :description test job, :recover? true, :durable? false, :test-dep nil, :disallow-concurrent-execution? nil, :dep-one {:a 1}, :dep-two #object[cronut.integration_test$eval13104$fn$reify__13106 0x45425cf3 cronut.integration_test$eval13104$fn$reify__13106@45425cf3]}
16:29:43.364 INFO  [CronutScheduler_Worker-5] cronut.integration-test – Reified Impl: {:dep-one {:a 1}}
16:29:44.007 INFO  [CronutScheduler_Worker-1] cronut.integration-test – Defrecord Impl: #cronut.integration_test.TestDefrecordJobImpl{:identity [test-group test-name], :description test job, :recover? true, :durable? false, :test-dep nil, :disallow-concurrent-execution? nil, :dep-one {:a 1}, :dep-two #object[cronut.integration_test$eval13104$fn$reify__13106 0x45425cf3 cronut.integration_test$eval13104$fn$reify__13106@45425cf3]}
16:29:44.375 INFO  [CronutScheduler_Worker-2] cronut.integration-test – Defrecord Impl: #cronut.integration_test.TestDefrecordJobImpl{:identity [test-group test-name], :description test job, :recover? true, :durable? false, :test-dep nil, :disallow-concurrent-execution? nil, :dep-one {:a 1}, :dep-two #object[cronut.integration_test$eval13104$fn$reify__13106 0x45425cf3 cronut.integration_test$eval13104$fn$reify__13106@45425cf3]}
16:29:45.368 INFO  [CronutScheduler_Worker-3] cronut.integration-test – Reified Impl: {:dep-one {:a 1}}
16:29:47.011 INFO  [CronutScheduler_Worker-6] cronut.integration-test – 3979b197-5683-47a9-a267-dcaded343697 Finished
16:29:47.368 INFO  [CronutScheduler_Worker-4] cronut.integration-test – Reified Impl: {:dep-one {:a 1}}
16:29:47.875 INFO  [CronutScheduler_Worker-5] cronut.integration-test – Defrecord Impl: #cronut.integration_test.TestDefrecordJobImpl{:identity [test-group test-name], :description test job, :recover? true, :durable? false, :test-dep nil, :disallow-concurrent-execution? nil, :dep-one {:a 1}, :dep-two #object[cronut.integration_test$eval13104$fn$reify__13106 0x45425cf3 cronut.integration_test$eval13104$fn$reify__13106@45425cf3]}
16:29:48.008 INFO  [CronutScheduler_Worker-1] cronut.integration-test – Defrecord Impl: #cronut.integration_test.TestDefrecordJobImpl{:identity [test-group test-name], :description test job, :recover? true, :durable? false, :test-dep nil, :disallow-concurrent-execution? nil, :dep-one {:a 1}, :dep-two #object[cronut.integration_test$eval13104$fn$reify__13106 0x45425cf3 cronut.integration_test$eval13104$fn$reify__13106@45425cf3]}
16:29:48.010 INFO  [CronutScheduler_Worker-2] cronut.integration-test – Defrecord Impl: #cronut.integration_test.TestDefrecordJobImpl{:identity [test-group test-name], :description test job, :recover? true, :durable? false, :test-dep nil, :disallow-concurrent-execution? nil, :dep-one {:a 1}, :dep-two #object[cronut.integration_test$eval13104$fn$reify__13106 0x45425cf3 cronut.integration_test$eval13104$fn$reify__13106@45425cf3]}
16:29:48.011 INFO  [CronutScheduler_Worker-3] cronut.integration-test – Defrecord Impl: #cronut.integration_test.TestDefrecordJobImpl{:identity [test-group test-name], :description test job, :recover? true, :durable? false, :test-dep nil, :disallow-concurrent-execution? nil, :dep-one {:a 1}, :dep-two #object[cronut.integration_test$eval13104$fn$reify__13106 0x45425cf3 cronut.integration_test$eval13104$fn$reify__13106@45425cf3]}
16:29:49.370 INFO  [CronutScheduler_Worker-6] cronut.integration-test – Reified Impl: {:dep-one {:a 1}}
16:29:50.004 INFO  [CronutScheduler_Worker-4] cronut.integration-test – 299b73c8-97ad-4d85-848f-35960ced6362 Reified Impl (Job Delay 7s): {}
16:29:51.368 INFO  [CronutScheduler_Worker-5] cronut.integration-test – Reified Impl: {:dep-one {:a 1}}
16:29:51.368 INFO  [CronutScheduler_Worker-1] cronut.integration-test – Defrecord Impl: #cronut.integration_test.TestDefrecordJobImpl{:identity [test-group test-name], :description test job, :recover? true, :durable? false, :test-dep nil, :disallow-concurrent-execution? nil, :dep-one {:a 1}, :dep-two #object[cronut.integration_test$eval13104$fn$reify__13106 0x45425cf3 cronut.integration_test$eval13104$fn$reify__13106@45425cf3]}
16:29:52.004 INFO  [CronutScheduler_Worker-2] cronut.integration-test – Defrecord Impl: #cronut.integration_test.TestDefrecordJobImpl{:identity [test-group test-name], :description test job, :recover? true, :durable? false, :test-dep nil, :disallow-concurrent-execution? nil, :dep-one {:a 1}, :dep-two #object[cronut.integration_test$eval13104$fn$reify__13106 0x45425cf3 cronut.integration_test$eval13104$fn$reify__13106@45425cf3]}
16:29:53.366 INFO  [CronutScheduler_Worker-3] cronut.integration-test – Reified Impl: {:dep-one {:a 1}}
16:29:54.007 INFO  [CronutScheduler_Worker-6] cronut.integration-test – Defrecord Impl: #cronut.integration_test.TestDefrecordJobImpl{:identity [test-group test-name], :description test job, :recover? true, :durable? false, :test-dep nil, :disallow-concurrent-execution? nil, :dep-one {:a 1}, :dep-two #object[cronut.integration_test$eval13104$fn$reify__13106 0x45425cf3 cronut.integration_test$eval13104$fn$reify__13106@45425cf3]}
16:29:54.874 INFO  [CronutScheduler_Worker-5] cronut.integration-test – Defrecord Impl: #cronut.integration_test.TestDefrecordJobImpl{:identity [test-group test-name], :description test job, :recover? true, :durable? false, :test-dep nil, :disallow-concurrent-execution? nil, :dep-one {:a 1}, :dep-two #object[cronut.integration_test$eval13104$fn$reify__13106 0x45425cf3 cronut.integration_test$eval13104$fn$reify__13106@45425cf3]}
```

### Stopping the system

```clojure
(test/halt-system *1)
```

# License

Distributed under the Apache 2.0 License.

Copyright (c) [Factor House](https://factorhouse.io)
