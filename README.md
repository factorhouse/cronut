# Cronut: Scheduled Execution via Quartz and Integrant

[![Clojars Project](https://img.shields.io/clojars/v/com.troy-west/cronut.svg)](https://clojars.org/com.troy-west/cronut) [![CircleCI](https://circleci.com/gh/troy-west/cronut.svg?style=svg)](https://circleci.com/gh/troy-west/cronut)

Cronut provides a data-first Clojure wrapper for the [Quartz Job Scheduler](http://www.quartz-scheduler.org/)

# Summary

Quartz is richly featured, open source job scheduling library that is fairly standard on the JVM.

Clojure has two existing wrappers for Quartz:

1. [Quartzite](https://github.com/michaelklishin/quartzite) by Michael Klishin / ClojureWerkz
2. [Twarc](https://github.com/prepor/twarc) by Andrew Rudenko / Prepor

How does Cronut differ?

1. Configured entirely from data (with [Integrant](https://github.com/weavejester/integrant) bindings provided)
2. No macros or new protocols, just implement the org.quartz.Job interface
3. No global Clojure state
4. Latest version of Quartz
5. Tagged literals to shortcut common use-cases (#cronut/cron, #cronut/interval)
6. Easily extensible for further triggers / tagged literals
7. Zero dependencies other than Clojure, Quartz, and Integrant
8. Configurable control of concurrent job execution

# Usage

## :cronut/scheduler

Cronut provides lifecycle implementation for the Quartz Scheduler, exposed via Integrant / `:cronut/scheduler`

The scheduler supports the following fields:

1. (required) :schedule - a sequence of 'items' to schedule, each being a map containing a :job and :trigger
2. (optional, default false) :disallowConcurrentExecution? - run all jobs with @DisableConcurrentExecution
2. (optional, default false) :update-check? check for Quartz updates on system startup.

e.g.

````clojure
:cronut/scheduler {:schedule  [{:job     #ig/ref :test.job/two
                                :trigger #cronut/interval 3500}
                               {:job     #ig/ref :test.job/two
                                :trigger #cronut/cron "*/8 * * * * ?"
                                :misfire :do-nothing}]
                   :disallowConcurrentExecution? true}}
````

### Controlling Concurrent Execution

Cronut 0.2.6+ supports the option to disable concurrent execution of jobs (see configuration, above).

This flag is set at a global level and affects all scheduled jobs. Raise a PR if you want to intermingle concurrency.

If you disable concurrent ob execution ensure you understand Quartz Misfire options and remember to set `org.quartz.jobStore.misfireThreshold=[some ms value]` in your quartz.properties file. See Quartz documentation for more information. 

See our test-resources/config.edn and test-resources/org/quartz/quartz.properties for examples.

### The :job

The `:job` in every scheduled item must implement the org.quartz.Job interface

The expectation being that every 'job' in your Integrant system will reify that interface, either directly via `reify`
or by returning a defrecord that implements the interface. e.g.

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

Cronut supports further Quartz configuration of jobs (identity, description, recovery, and priority) by expecting those
values to be assoc'd onto your job. You do not have to set them (in fact in most cases you can likely ignore them),
however if you do want that control you will likely use the defrecord approach as opposed to the simpler reify option
and pass that configuration through edn, e.g.

````clojure
:test.job/two     {:identity    ["job-two" "test"]
                   :description "test job"
                   :recover?    true
                   :durable?    false
                   :dep-one     #ig/ref :dep/one
                   :dep-two     #ig/ref :test.job/one}
````                    

### The :trigger

The `:trigger` in every scheduled item must resolve to an org.quartz.Trigger of some variety or another, to ease that 
resolution Cronut provides the following tagged literals:

### Tagged Literals

#### #cronut/cron: Simple Cron Scheduling

A job is scheduled to run on a cron by using the `#cronut/cron` tagged literal followed by a valid cron expression

The job will start immediately when the system is initialized, and runs in the default system time-zone

````clojure
:trigger #cronut/cron "*/8 * * * * ?"
````

#### #cronut/interval: Simple Interval Scheduling

A job is scheduled to run periodically by using the `#cronut/interval` tagged literal followed by a milliseconds value 

````clojure
:trigger #cronut/interval 3500
````

#### #cronut/trigger: Full (and extensible) Trigger Definition

Both #cronut/cron and #cronut/interval are effectively shortcuts to full trigger definition with sensible defaults.

The #cronut/trigger tagged literal supports the full set of Quartz configuration for Simple and Cron triggers:

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

This tagged literal calls a Clojure multi-method that is open for extension, see: [troy-west.cronut/trigger-builder](https://github.com/troy-west/cronut/blob/01ada3182ff18ec4a78095cdba80d43f660d8c85/src/troy_west/cronut.clj#L58)

You should implement the remaining two Quartz Triggers (CalendarInterval and DailyTimeInterval), create the Tagged
Literal for each, and raise a PR. Go on it will be fun, open issues exist for both triggers.

## Integrant

When initializing an Integrant system you will need to provide the Cronut data readers.

See: `troy-west.cronut/data-readers` for convenience.

````clojure
(def data-readers
  {'cronut/trigger  troy-west.cronut/trigger-builder
   'cronut/cron     troy-west.cronut/shortcut-cron
   'cronut/interval troy-west.cronut/shortcut-interval})
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

## Quartz Specifics and Remaining Todo's

Cronut supports a single Quartz Scheduler per JVM (optionally configured with [quartz.properties](http://www.quartz-scheduler.org/documentation/quartz-2.x/configuration/ConfigMain.html)).

The default StdScheduler is reset and re-used on each instantiation of a :cronut/scheduler.

Cron triggers default to using the system time-zone if no trigger time-zone specifically set.

Tickets are open for the following extensions (contributions warmly welcomed): 

* Implement DailyTimeInterval Trigger
* Implement CalendarInverval Trigger
* Pluggable SchedulerFactory (support more than one scheduler per JVM)

## Example System

Given a simple Integrant configuration of two jobs and four triggers.

Job Two executes on multiple schedules as defined by the latter three triggers. 

````clojure
{:test.job/one     {}

 :test.job/two     {:identity    ["job-two" "test"]
                    :description "test job"
                    :recover?    true
                    :durable?    false
                    :dep-two     #ig/ref :test.job/one}

 :cronut/scheduler {:schedule  [;; basic interval
                                {:job     #ig/ref :test.job/one
                                 :trigger #cronut/trigger {:type      :simple
                                                           :interval  2
                                                           :time-unit :seconds
                                                           :repeat    :forever}}
                             
                                ;; shortcut interval via cronut/interval data-reader
                                {:job     #ig/ref :test.job/two
                                 :trigger #cronut/interval 3500}

                                ;; basic cron
                                {:job     #ig/ref :test.job/two
                                 :trigger #cronut/trigger {:type :cron
                                                           :cron "*/4 * * * * ?"}}
                             
                                ;; shortcut cron via cronut/cron data-reader
                                {:job     #ig/ref :test.job/two
                                 :trigger #cronut/cron "*/8 * * * * ?"}]}}

````

And the associated Integrant lifecycle impl, note:

- `test.job/one` reifies the org.quartz.Job interface
- `test.job/two` instantiates a defrecord (that allows some further quartz job configuration)  

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

We can realise that system and run those jobs (See `troy-west.cronut.integration-fixture` for full example):

````clojure
(require '[troy-west.cronut.integration-fixture :as itf])
=> nil

(itf/initialize!)
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
 
 (itf/shutdown!)
=> nil 
````

## License

Copyright © 2018 [Troy-West, Pty Ltd.](http://www.troywest.com)

Distributed under the Eclipse Public License either version 2.0 or (at your option) any later version.
