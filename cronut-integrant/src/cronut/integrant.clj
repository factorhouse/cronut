(ns cronut.integrant
  (:require [cronut :as cronut]
            [cronut.trigger]
            [integrant.core :as ig]))

(defmethod ig/init-key :cronut/scheduler
  [_ {:keys [schedule] :as config}]
  (let [scheduler (cronut/scheduler config)]
    (cronut/clear scheduler)
    (cronut/schedule-jobs scheduler schedule)
    (cronut/start scheduler)))

(defmethod ig/halt-key! :cronut/scheduler
  [_ scheduler]
  (cronut/shutdown scheduler))

(def data-readers
  {'cronut/trigger  cronut.trigger/builder
   'cronut/cron     cronut.trigger/cron
   'cronut/interval cronut.trigger/interval})