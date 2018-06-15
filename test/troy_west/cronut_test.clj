(ns troy-west.cronut-test
  (:require [clojure.test :refer :all]
            [troy-west.cronut :as cronut])
  (:import (sun.util.calendar ZoneInfo)))

(deftest base-trigger

  (is (= {:group       "DEFAULT"
          :description nil
          :priority    5}
         (-> (cronut/base-trigger-builder {})
             (.build)
             (bean)
             (select-keys [:group :description :priority]))))

  (is (= {:group       "test"
          :name        "trigger-two"
          :description "test trigger"
          :priority    101
          :startTime   #inst "2019-01-01T00:00:00.000-00:00"
          :endTime     #inst "2019-02-01T00:00:00.000-00:00"}
         (-> (cronut/base-trigger-builder
              {:identity    ["trigger-two" "test"]
               :description "test trigger"
               :start       #inst "2019-01-01T00:00:00.000-00:00"
               :end         #inst "2019-02-01T00:00:00.000-00:00"
               :priority    101})
             (.build)
             (bean)
             (select-keys [:group :name :description :startTime :endTime :priority])))))

(deftest simple-schedule

  (is (= {:repeatCount        0
          :repeatInterval     0
          :misfireInstruction 0}
         (-> (cronut/simple-schedule nil)
             (.build)
             (bean)
             (select-keys [:repeatInterval :repeatCount :misfireInstruction]))))

  (is (= {:repeatCount        0
          :repeatInterval     1000
          :misfireInstruction 0}
         (-> (cronut/simple-schedule {:interval 1000})
             (.build)
             (bean)
             (select-keys [:repeatInterval :repeatCount :misfireInstruction]))))

  (is (= {:repeatCount        -1
          :repeatInterval     1000
          :misfireInstruction 0}
         (-> (cronut/simple-schedule {:interval 1000
                                      :repeat   :forever})
             (.build)
             (bean)
             (select-keys [:repeatInterval :repeatCount :misfireInstruction]))))

  (is (= {:repeatCount        10
          :repeatInterval     1000
          :misfireInstruction 0}
         (-> (cronut/simple-schedule {:interval 1000
                                      :repeat   10})
             (.build)
             (bean)
             (select-keys [:repeatInterval :repeatCount :misfireInstruction]))))

  (is (= {:repeatCount        10
          :repeatInterval     1000000
          :misfireInstruction 0}
         (-> (cronut/simple-schedule {:interval  1000
                                      :repeat    10
                                      :time-unit :seconds})
             (.build)
             (bean)
             (select-keys [:repeatInterval :repeatCount :misfireInstruction]))))

  (is (= {:repeatCount        10
          :repeatInterval     1000000
          :misfireInstruction 5}
         (-> (cronut/simple-schedule {:interval  1000
                                      :repeat    10
                                      :time-unit :seconds
                                      :misfire   :next-existing})
             (.build)
             (bean)
             (select-keys [:repeatInterval :repeatCount :misfireInstruction])))))

(deftest cron-schedule

  (is (thrown? IllegalArgumentException
               (cronut/cron-schedule {})))

  (is (= {:cronExpression     "*/6 * * * * ?"
          :timeZone           (ZoneInfo/getDefault)
          :misfireInstruction 0}
         (-> (cronut/cron-schedule {:cron "*/6 * * * * ?"})
             (.build)
             (bean)
             (select-keys [:cronExpression :timeZone :misfireInstruction]))))

  (is (= {:cronExpression     "*/6 * * * * ?"
          :timeZone           (ZoneInfo/getTimeZone "UTC")
          :misfireInstruction 0}
         (-> (cronut/cron-schedule {:cron      "*/6 * * * * ?"
                                    :time-zone "UTC"})
             (.build)
             (bean)
             (select-keys [:cronExpression :timeZone :misfireInstruction]))))

  (is (= {:cronExpression     "*/6 * * * * ?"
          :timeZone           (ZoneInfo/getTimeZone "UTC")
          :misfireInstruction 1}
         (-> (cronut/cron-schedule {:cron      "*/6 * * * * ?"
                                    :time-zone "UTC"
                                    :misfire   :fire-and-proceed})
             (.build)
             (bean)
             (select-keys [:cronExpression :timeZone :misfireInstruction])))))

