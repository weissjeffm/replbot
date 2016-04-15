(ns replbot.plugins.weather
  (:require [ororo.core :as w]
            [replbot.core :refer [command command-args config match-first]]
            [replbot.plugin :as plugin]
            [clojure.string :as string])
  (:import [replbot.plugin Plugin PluginDocs ActivationDocs]))

(defn parse-location [args]
  (if (= 1 (count args))
    (first args)
    [(first args) (second args)]))

(defn conditions [token location]
  (let [data (w/conditions token location)]
    (if (map? data)
      (apply format
             (str "%s; %s; Dewpoint: %s; Precipitation today: %s; "
                  "Temperature: %s; Windchill: %s; "
                  "Wind speed: %smph; Wind gust: %smph; URL: %s.")
             ((juxt :observation_time :weather :dewpoint_string
                    :precip_today_string :temperature_string :windchill_string
                    :wind_mph :wind_gust_mph :forecast_url)
              data))
      "Location not found.")))

(defn extract-time [k]
  (fn [data]
    (string/join ":" ((juxt :hour :minute) (k data)))))

(defn astronomy [token location]
  (apply format
         (str "Percentage of moon illuminated: %s; Age of moon: %s; "
              "Current time: %s; Sunset: %s; Sunrise: %s.")
         ((juxt :percentIlluminated :ageOfMoon (extract-time :current_time)
                (extract-time :sunset) (extract-time :sunrise))
          (w/astronomy token location))))

(def weather
  (partial match-first
           [[(partial command-args :conditions)
             (fn [_ _ location]
               (conditions (-> config :plugins :weather :token) location))]
            [(partial command-args :astronomy)
             (fn [_ _ location]
               (astronomy (-> config :plugins :weather :token) location))]]))

(def plugin
  (Plugin. #'weather
           (PluginDocs. "Weather"
                        "Gives weather information about a given location."
                        [(ActivationDocs. :conditions [:conditions :location] "Current weather conditions at 'location'.")
                         (ActivationDocs. :astronomy [:astronomy :location] "Moon and sun parameters at 'location'.")])
           nil))

(defmethod replbot.plugin/get-all (ns-name *ns*) [_]
  {:weather plugin})
