# Cronut: A Clojure Companion to Quartz

[![Cronut Test](https://github.com/factorhouse/cronut/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/factorhouse/cronut/actions/workflows/ci.yml)
[![Clojars Project](https://img.shields.io/clojars/v/io.factorhouse/cronut.svg)](https://clojars.org/io.factorhouse/cronut)

# Summary

[Cronut](https://github.com/factorhouse/cronut) provides a data-first [Clojure](https://clojure.org/) wrapper
for [Quartz Scheduler](https://github.com/quartz-scheduler/quartz) version `2.5.0`, compatible
with [Jakarta](https://en.wikipedia.org/wiki/Jakarta_EE).

Cronut supports **in-memory** scheduling of jobs within a single JVM. JDBC and distributed jobstore are not supported.

## Related Projects

| Project                                                             | Desription                                                                                                   | Clojars Project                                                                                                                                 |
|---------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| [cronut-javax](https://github.com/factorhouse/cronut-javax)         | Cronut with [Javax](https://jakarta.ee/blogs/javax-jakartaee-namespace-ecosystem-progress/) support (Legacy) | [![Clojars Project](https://img.shields.io/clojars/v/io.factorhouse/cronut-javax.svg)](https://clojars.org/io.factorhouse/cronut-javax)         |
| [cronut-integrant](https://github.com/factorhouse/cronut-integrant) | [Integrant](https://github.com/weavejester/integrant) bindings for Cronut                                    | [![Clojars Project](https://img.shields.io/clojars/v/io.factorhouse/cronut-integrant.svg)](https://clojars.org/io.factorhouse/cronut-integrant) |

# Contents

- [Usage](#usage)
    * [Scheduler](#scheduler)
        + [Scheduler lifecycle](#scheduler-lifecycle)
        + [Scheduling jobs](#scheduling-jobs)
    * [Jobs](#jobs)
        + [Job example](#job-example)
    * [Triggers](#triggers)
      - [`cronut.trigger/cron`: Simple Cron Scheduling](#cronuttriggercron-simple-cron-scheduling)
      - [`cronut.trigger/interval`: Simple Interval Scheduling](#cronuttriggerinterval-simple-interval-scheduling)
      - [`cronut.trigger/builder`: Full trigger definition](#cronuttriggerbuilder-full-trigger-definition)
    * [Concurrent execution](#concurrent-execution)
        + [Global concurrent execution](#global-concurrent-execution)
        + [Job-specific concurrent execution](#job-specific-concurrent-execution)
        + [Misfire configuration](#misfire-configuration)
- [Example system](#example-system)
- [License](#license)

# Usage

A quartz `scheduler` runs a `job` on a schedule defined by a `trigger`.

## Scheduler

Cronut provides access to the Quartz Scheduler, exposed via the `cronut/scheduler` function.

Create a scheduler with the following configuration:

1. `:concurrent-execution-disallowed?`: (optional, default false) - run all jobs with @DisableConcurrentExecution
2. `:update-check?`: (optional, default false) - check for Quartz updates on system startup.

````clojure
(cronut/scheduler {:concurrent-execution-disallowed? true
                   :update-check?                    false})
````

### Scheduler lifecycle

Once created, you can:

* `cronut/start`: start the scheduler
* `cronut/start-delayed`: start the scheduler with a delay
* `cronut/standy`: temporarily halt the firing of triggers by the scheduler
* `cronut/shutdown`: stop the scheduler
* `cronut/pause-all`: pause all triggers
* `cronut/resume-all`: resume all triggers
* `cronut/clear`: clear all scheduling data of jobs and triggers

### Scheduling jobs

To schedule jobs, you can

* `cronut/schedule-job`: schedule a single job
* `cronut/schedule-jobs`: schedule multiple jobs at once
* `cronut/pause-job`: pause a job
* `cronut/resume-job`: resume a paused job
* `cronut/unschedule-job`: remove a trigger from the scheduler
* `cronut/delete-job`: remove a job and all associated triggers from the scheduler
* `cronut/pause-trigger`: pause a trigger
* `cronut/resume-trigger`: resume a paused trigger

## Jobs

Each cronut job must implement the `org.quartz.Job` interface.

The expectation being that every job will reify that interface either directly via `reify` or by returning a `defrecord`
that implements the interface.

Cronut supports further Quartz configuration of jobs (identity, description, recovery, and durability) by expecting
those values to be assoc'd onto your job. You do not have to set them (in fact in most cases you can likely ignore
them), however if you do want that control you will likely use the `defrecord` approach as opposed to `reify`.

Concurrent execution can be controlled on a per-job bases with the `disallow-concurrent-execution?` flag.

### Job example

````clojure
(defrecord TestDefrecordJobImpl [identity description recover? durable?]
  Job
  (execute [this _job-context]
    (log/info "Defrecord Impl:" this)))


(let [scheduler     (cronut/scheduler {:concurrent-execution-disallowed? true
                                       :update-check?                    false})
      defrecord-job (map->TestDefrecordJobImpl {:identity    ["name1" "group2"]
                                                :description "test job"
                                                :recover?    true
                                                :durable?    false})
      reify-job     (reify Job
                      (execute [_this _job-context]
                        (let [rand-id (str (UUID/randomUUID))]
                          (log/info rand-id "Reified Impl"))))]

  (cronut/schedule-job scheduler (trigger/interval 1000) defrecord-job)

  (cronut/schedule-job scheduler
                       (trigger/builder {:type    :cron
                                         :cron    "*/5 * * * * ?"
                                         :misfire :do-nothing})
                       reify-job))
```` 

## Triggers

Cronut triggers are of type `org.quartz.Trigger`, the following functions are provided to simplify trigger creation:

#### `cronut.trigger/cron`: Simple Cron Scheduling

A job is scheduled to run on a cron by using the `cronut.trigger/cron` function with a valid cron expression.

The job will start immediately when the system is initialized, and runs in the default system time-zone

````clojure
(cronut.trigger/cron "*/8 * * * * ?")
````

#### `cronut.trigger/interval`: Simple Interval Scheduling

A job is scheduled to run periodically by using the `cronut.trigger/interval` function with a milliseconds value

````clojure
(cronut.trigger/interval 3500)
````

#### `cronut.trigger/builder`: Full trigger definition

Both `cronut.trigger/cron` and `cronut.trigger/interval` are effectively shortcuts to full trigger definition with
sensible defaults.

The `cronut.trigger/builder` function supports the full set of Quartz configuration triggers:

````clojure
;; interval
(cronut.trigger/builder {:type        :simple
                         :interval    3000
                         :repeat      :forever
                         :identity    ["trigger-two" "test"]
                         :description "sample simple trigger"
                         :start       #inst "2019-01-01T00:00:00.000-00:00"
                         :end         #inst "2019-02-01T00:00:00.000-00:00"
                         :misfire     :ignore
                         :priority    5})

;;cron
(cronut.trigger/builder {:type        :cron
                         :cron        "*/6 * * * * ?"
                         :identity    ["trigger-five" "test"]
                         :description "sample cron trigger"
                         :start       #inst "2018-01-01T00:00:00.000-00:00"
                         :end         #inst "2029-02-01T00:00:00.000-00:00"
                         :time-zone   "Australia/Melbourne"
                         :misfire     :fire-and-proceed
                         :priority    4})
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

# Example system

See: integration test source: [test/cronut/integration-test.clj](test/cronut/integration_test.clj).

````clojure
(ns cronut.integration-test
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [cronut :as cronut]
            [cronut.trigger :as trigger])
  (:import (java.util UUID)
           (org.quartz Job)))

(defrecord TestDefrecordJobImpl [identity description recover? durable? test-dep disallowConcurrentExecution?]
  Job
  (execute [this _job-context]
    (log/info "Defrecord Impl:" this)))

(def reify-job (reify Job
                 (execute [_this _job-context]
                   (let [rand-id (str (UUID/randomUUID))]
                     (log/info rand-id "Reified Impl (Job Delay 7s)")
                     (async/<!! (async/timeout 7000))
                     (log/info rand-id "Finished")))))

;(do (require '[cronut.integration-test :as it])
;    (it/test-system))
(defn test-system
  []
  (let [scheduler (cronut/scheduler {:concurrent-execution-disallowed? true})]
    (cronut/clear scheduler)

    (async/<!! (async/timeout 2000))

    (log/info "scheduling defrecord job on 1s interval")
    (cronut/schedule-job scheduler
                         (trigger/interval 1000)
                         (map->TestDefrecordJobImpl {:identity    ["name1" "group2"]
                                                     :description "test job"
                                                     :recover?    true
                                                     :durable?    false}))

    ;; demonstrate scheduler can start with jobs, and jobs can start after scheduler
    (cronut/start scheduler)

    (async/<!! (async/timeout 2000))

    ;; demonstrates concurrency disallowed (every second job runs, 10s interval between jobs that should run every 5s)
    (log/info "scheduling reify/7s/no-misfire job on 5s interval")
    (cronut/schedule-job scheduler
                         (trigger/builder {:type    :cron
                                           :cron    "*/5 * * * * ?"
                                           :misfire :do-nothing})
                         reify-job)

    (async/<!! (async/timeout 15000))

    (log/info "deleting job group2.name1")
    (cronut/delete-job scheduler "name1" "group2")

    (async/<!! (async/timeout 15000))

    (cronut/shutdown scheduler)))
````

# License

Distributed under the Apache 2.0 License.

Copyright (c) [Factor House](https://factorhouse.io)
