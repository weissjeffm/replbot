(ns replbot.plugins.karma
  (:require [replbot.core :refer [command]]
            [replbot.plugin :as plugin])
  (:import [replbot.plugin Plugin PluginDocs ActivationDocs]
           [org.jxmpp.util XmppStringUtils]))

(defn karma [state packet]
  (let [source (-> packet :from XmppStringUtils/parseResource)]
    (if-let [[_ _ target] (or (re-find #"^\((inc)\s+(.+)\)" (:body packet))
                             (re-find #"^([Tt]hanks,?|[Tt]hank you,?)\s+(.+)" (:body packet)))]
     (let [target (.trim target)
           inc (fnil inc 0)
           dec (fnil dec 0)
           transfer-karma (fn [karma-map source target]
                            (-> karma-map
                                (update-in [target] inc)
                                (update-in [source] dec)))]
       (if (= source target)
         "You can't give karma to yourself."
         (do (swap! state transfer-karma source target)
             (format "%s: %s" target (-> state deref (get target))))))
     (if-let [[command args] (command packet)]
       (when (= command :karma)
         (let [target (or (let [trimmed (.trim args)]
                            (when (-> trimmed count (> 0))
                              trimmed))
                          source)]
           (format "%s: %s" target (let [target-score (.get @state target)]
                                     (or target-score "No karma")))))
       (when-let [[_ _ target] (re-find #"^\((dec)\s+(.+)\)" (:body packet))]
         (format "Karma can only be given, not taken. But your displeasure with %s is duly noted." target))))))

(def plugin
  (Plugin. #'karma
           (PluginDocs. "Karma"
                        "Karma is zero-sum (giving a point takes a point from you). To help you remember to give karma when it is due, thanking someone is sufficient to transfer karma."
                        [(ActivationDocs. :inc
                                          "(inc Bob)"
                                          "Give a karma point to Bob")
                         (ActivationDocs. :dec
                                          "(dec Bob)"
                                          "Express displeasure with Bob (no karma is transferred because it can only be given, not taken.)")
                         (ActivationDocs. :karma [:karma :someone] "Show someone's karma balance.")])
           (atom {})))

(defmethod replbot.plugin/get-all (ns-name *ns*) [_]
  {:karma plugin})
