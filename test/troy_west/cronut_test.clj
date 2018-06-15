(ns troy-west.cronut-test
  (:require [clojure.test :refer :all]
            [troy-west.cronut :as cronut])
  (:import (org.quartz TriggerBuilder)))

(deftest base-trigger

  (is (= {:group       "test"
          :name        "trigger-two"
          :description "test trigger"
          :priority    101
          :startTime   #inst "2019-01-01T00:00:00.000-00:00"
          :endTime     #inst "2019-02-01T00:00:00.000-00:00"}
         (select-keys
          (bean
           (.build ^TriggerBuilder (cronut/base-trigger-builder
                                    {:identity    ["trigger-two" "test"]
                                     :description "test trigger"
                                     :start       #inst "2019-01-01T00:00:00.000-00:00"
                                     :end         #inst "2019-02-01T00:00:00.000-00:00"
                                     :priority    101})))
          [:group :name :description :startTime :endTime :priority]))))