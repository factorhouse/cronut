(ns cronut.trigger-test
  (:require [clojure.test :refer [deftest is]]
            [cronut.trigger :as trigger])
  (:import (java.util TimeZone)))

(deftest base-trigger

  (is (= {:group       "DEFAULT"
          :description nil
          :priority    5}
         (-> (trigger/base-builder {})
             (.build)
             (bean)
             (select-keys [:group :description :priority]))))

  ;; :name is required before :name :group identity takes effect
  (is (= {:group       "DEFAULT"
          :description nil
          :priority    5}
         (-> (trigger/base-builder {:group "trigger-group"})
             (.build)
             (bean)
             (select-keys [:group :description :priority]))))

  (is (= {:name        "trigger-name"
          :group       "trigger-group"
          :description "test trigger"
          :priority    101
          :startTime   #inst "2019-01-01T00:00:00.000-00:00"
          :endTime     #inst "2019-02-01T00:00:00.000-00:00"}
         (-> (trigger/base-builder
              {:name        "trigger-name"
               :group       "trigger-group"
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
         (-> (trigger/simple-schedule nil)
             (.build)
             (bean)
             (select-keys [:repeatInterval :repeatCount :misfireInstruction]))))

  (is (= {:repeatCount        0
          :repeatInterval     1000
          :misfireInstruction 0}
         (-> (trigger/simple-schedule {:interval 1000})
             (.build)
             (bean)
             (select-keys [:repeatInterval :repeatCount :misfireInstruction]))))

  (is (= {:repeatCount        -1
          :repeatInterval     1000
          :misfireInstruction 0}
         (-> (trigger/simple-schedule {:interval 1000
                                       :repeat   :forever})
             (.build)
             (bean)
             (select-keys [:repeatInterval :repeatCount :misfireInstruction]))))

  (is (= {:repeatCount        10
          :repeatInterval     1000
          :misfireInstruction 0}
         (-> (trigger/simple-schedule {:interval 1000
                                       :repeat   10})
             (.build)
             (bean)
             (select-keys [:repeatInterval :repeatCount :misfireInstruction]))))

  (is (= {:repeatCount        10
          :repeatInterval     1000000
          :misfireInstruction 0}
         (-> (trigger/simple-schedule {:interval  1000
                                       :repeat    10
                                       :time-unit :seconds})
             (.build)
             (bean)
             (select-keys [:repeatInterval :repeatCount :misfireInstruction]))))

  (is (= {:repeatCount        10
          :repeatInterval     1000000
          :misfireInstruction 5}
         (-> (trigger/simple-schedule {:interval  1000
                                       :repeat    10
                                       :time-unit :seconds
                                       :misfire   :next-existing})
             (.build)
             (bean)
             (select-keys [:repeatInterval :repeatCount :misfireInstruction])))))

(deftest cron-schedule

  (is (thrown? IllegalArgumentException
               (trigger/cron-schedule {})))

  (is (= {:cronExpression     "*/6 * * * * ?"
          :timeZone           (TimeZone/getDefault)
          :misfireInstruction 0}
         (-> (trigger/cron-schedule {:cron "*/6 * * * * ?"})
             (.build)
             (bean)
             (select-keys [:cronExpression :timeZone :misfireInstruction]))))

  (is (= {:cronExpression     "*/6 * * * * ?"
          :timeZone           (TimeZone/getTimeZone "UTC")
          :misfireInstruction 0}
         (-> (trigger/cron-schedule {:cron      "*/6 * * * * ?"
                                     :time-zone "UTC"})
             (.build)
             (bean)
             (select-keys [:cronExpression :timeZone :misfireInstruction]))))

  (is (= {:cronExpression     "*/6 * * * * ?"
          :timeZone           (TimeZone/getTimeZone "UTC")
          :misfireInstruction 1}
         (-> (trigger/cron-schedule {:cron      "*/6 * * * * ?"
                                     :time-zone "UTC"
                                     :misfire   :fire-and-proceed})
             (.build)
             (bean)
             (select-keys [:cronExpression :timeZone :misfireInstruction])))))