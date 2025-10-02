(ns cronut.trigger
  (:refer-clojure :exclude [key])
  (:import (java.util TimeZone)
           (org.quartz CronScheduleBuilder SimpleScheduleBuilder TriggerBuilder TriggerKey)))

(defn base-builder
  "Provide a base trigger-builder from configuration"
  [{:keys [name group description start end priority]}]
  (cond-> (TriggerBuilder/newTrigger)
    name (.withIdentity name group)
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

(defmulti builder :type)

(defmethod builder :simple
  [config]
  (.withSchedule ^TriggerBuilder (base-builder config) (simple-schedule config)))

(defmethod builder :cron
  [config]
  (.withSchedule ^TriggerBuilder (base-builder config) (cron-schedule config)))

(defn interval
  "Trigger immediately, at an interval-ms, run forever (well that's optimistic but you get the idea)"
  [interval-ms]
  (builder {:type      :simple
            :interval  interval-ms
            :time-unit :millis
            :repeat    :forever}))

(defn cron
  "Trigger on a schedule defined by the cron expression"
  [cron]
  (builder {:type :cron
            :cron cron}))

(defn key
  ([name]
   (key name nil))
  ([name group]
   (TriggerKey. name group)))