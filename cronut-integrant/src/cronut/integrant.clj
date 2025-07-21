(ns cronut.integrant
  (:require [cronut :as cronut]
            [integrant.core :as ig]))

(defmethod ig/init-key :cronut/scheduler
  [_ config]
  (cronut/initialize config))

(defmethod ig/halt-key! :cronut/scheduler
  [_ scheduler]
  (cronut/shutdown scheduler))

(def data-readers
  {'cronut/trigger  cronut/trigger-builder
   'cronut/cron     cronut/shortcut-cron
   'cronut/interval cronut/shortcut-interval})