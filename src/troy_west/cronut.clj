(ns troy-west.cronut
  (:require [integrant.core :as ig]
            [clojure.string :as str]
            [troy-west.cronut.scheduler :as scheduler]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log])
  (:import (java.time.temporal ChronoUnit)
           (java.time ZoneId)
           (org.quartz JobExecutionContext)))

(defn chrono-unit
  [text]
  ;; If you're using Cursive and see the arity highlight below: https://github.com/cursive-ide/cursive/issues/1988
  (ChronoUnit/valueOf (str/upper-case text)))

(defn zone-id
  [text]
  (ZoneId/of text))

(defmethod ig/init-key :cronut/time-unit
  [_ text]
  (chrono-unit text))

(defmethod ig/init-key :cronut/time-zone
  [_ text]
  (zone-id text))

(defmethod ig/init-key :cronut/scheduler
  [_ config]
  (scheduler/initialize config))

(defmethod ig/halt-key! :cronut/scheduler
  [_ scheduler]
  (scheduler/shutdown scheduler))

(defmethod ig/init-key :cronut/job
  [_ config]
  )

(def data-readers
  {'ig/ref                 ig/ref
   'cronut/time-unit       troy-west.cronut/chrono-unit
   'cronut/time-zone       troy-west.cronut/zone-id
   'cronut/simple-schedule troy-west.cronut.schedule.simple/initialize})

(defn init
  [config]
  (ig/init (edn/read-string
            {:readers data-readers}
            config)))

(defn halt!
  [system]
  (ig/halt! system))