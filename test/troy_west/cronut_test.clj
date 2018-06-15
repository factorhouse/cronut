(ns troy-west.cronut-test
  (:require [clojure.test :refer :all]
            [troy-west.cronut :as cronut])
  (:import (org.quartz TriggerBuilder SimpleScheduleBuilder CronScheduleBuilder)
           (sun.util.calendar ZoneInfo)))

(deftest base-trigger

  (is (= {:group       "DEFAULT"
          :description nil
          :priority    5}
         (select-keys
          (bean
           (.build ^TriggerBuilder
                   (cronut/base-trigger-builder {})))
          [:group :description :priority])))

  (is (= {:group       "test"
          :name        "trigger-two"
          :description "test trigger"
          :priority    101
          :startTime   #inst "2019-01-01T00:00:00.000-00:00"
          :endTime     #inst "2019-02-01T00:00:00.000-00:00"}
         (select-keys
          (bean
           (.build ^TriggerBuilder
                   (cronut/base-trigger-builder
                    {:identity    ["trigger-two" "test"]
                     :description "test trigger"
                     :start       #inst "2019-01-01T00:00:00.000-00:00"
                     :end         #inst "2019-02-01T00:00:00.000-00:00"
                     :priority    101})))
          [:group :name :description :startTime :endTime :priority]))))

(deftest simple-schedule

  (is (= {:repeatCount        0
          :repeatInterval     0
          :misfireInstruction 0}
         (select-keys
          (bean
           (.build ^SimpleScheduleBuilder
                   (cronut/simple-schedule nil)))
          [:repeatInterval :repeatCount :misfireInstruction])))

  (is (= {:repeatCount        0
          :repeatInterval     1000
          :misfireInstruction 0}
         (select-keys
          (bean
           (.build ^SimpleScheduleBuilder
                   (cronut/simple-schedule {:interval 1000})))
          [:repeatInterval :repeatCount :misfireInstruction])))

  (is (= {:repeatCount        -1
          :repeatInterval     1000
          :misfireInstruction 0}
         (select-keys
          (bean
           (.build ^SimpleScheduleBuilder
                   (cronut/simple-schedule {:interval 1000
                                            :repeat   :forever})))
          [:repeatInterval :repeatCount :misfireInstruction])))

  (is (= {:repeatCount        10
          :repeatInterval     1000
          :misfireInstruction 0}
         (select-keys
          (bean
           (.build ^SimpleScheduleBuilder
                   (cronut/simple-schedule {:interval 1000
                                            :repeat   10})))
          [:repeatInterval :repeatCount :misfireInstruction])))

  (is (= {:repeatCount        10
          :repeatInterval     1000000
          :misfireInstruction 0}
         (select-keys
          (bean
           (.build ^SimpleScheduleBuilder
                   (cronut/simple-schedule {:interval  1000
                                            :repeat    10
                                            :time-unit :seconds})))
          [:repeatInterval :repeatCount :misfireInstruction])))

  (is (= {:repeatCount        10
          :repeatInterval     1000000
          :misfireInstruction 5}
         (select-keys
          (bean
           (.build ^SimpleScheduleBuilder
                   (cronut/simple-schedule {:interval  1000
                                            :repeat    10
                                            :time-unit :seconds
                                            :misfire   :next-existing})))
          [:repeatInterval :repeatCount :misfireInstruction]))))

(deftest cron-schedule

  (is (thrown? IllegalArgumentException
               (cronut/cron-schedule {})))

  (is (= {:cronExpression     "*/6 * * * * ?"
          :timeZone           (ZoneInfo/getDefault)
          :misfireInstruction 0}
         (select-keys
          (bean
           (.build ^CronScheduleBuilder
                   (cronut/cron-schedule {:cron "*/6 * * * * ?"})))
          [:cronExpression :timeZone :misfireInstruction])))

  (is (= {:cronExpression     "*/6 * * * * ?"
          :timeZone           (ZoneInfo/getTimeZone "UTC")
          :misfireInstruction 0}
         (select-keys
          (bean
           (.build ^CronScheduleBuilder
                   (cronut/cron-schedule {:cron      "*/6 * * * * ?"
                                          :time-zone "UTC"})))
          [:cronExpression :timeZone :misfireInstruction])))

  (is (= {:cronExpression     "*/6 * * * * ?"
          :timeZone           (ZoneInfo/getTimeZone "UTC")
          :misfireInstruction 1}
         (select-keys
          (bean
           (.build ^CronScheduleBuilder
                   (cronut/cron-schedule {:cron      "*/6 * * * * ?"
                                          :time-zone "UTC"
                                          :misfire   :fire-and-proceed})))
          [:cronExpression :timeZone :misfireInstruction]))))

