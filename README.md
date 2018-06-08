# Cronut: Scheduled Execution via Quartz and Integrant

> "Cronut is a good name, you can call it that if you want to" - James Sofra

Cronut provides a data-first Clojure wrapper for the [Quartz Job Scheduler](http://www.quartz-scheduler.org/)

[![Clojars Project](https://img.shields.io/clojars/v/com.troy-west/cronut.svg)](https://clojars.org/com.troy-west/cronut)

# Summary

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

# Usage

Example: Two jobs and three triggers in a simple Integrant system. 

````clojure
{:test.job/one     {:dep-one #ig/ref :dep/one}

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

Example: the associated Integrant lifecycle impl, note:

- test.job/one reifies the org.quartz.Job interface
- test.job/two instantiates a defrecord (that allows some further quartz job configuration)  

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

See troy-west.cronut.integration-fixture for full example:

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