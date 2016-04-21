(ns replbot.plugins.karma
  (:require [replbot.core :as core]
            [replbot.plugin :as plugin]
            [alandipert.enduro :as e])
  (:import [replbot.plugin Plugin PluginDocs ActivationDocs]
           [org.jxmpp.util XmppStringUtils]))

(defn inc-match [_ packet]
  (or (re-find #"^\((inc)\s+(.+)\)" (:body packet))
      (re-find #"^([Tt]hanks,?|[Tt]hank you,?)\s+(.+)" (:body packet))))

(defn dec-match [_ packet]
  (re-find #"^\((dec)\s+(.+)\)" (:body packet)))

(defn source "Returns the nick of the sender of packet in a muc"
  [packet]
  (-> packet :from XmppStringUtils/parseResource))

(defn transfer-karma [state source target]
  (-> state
      (update-in [target] (fnil inc 0))
      (update-in [source] (fnil dec 0))))

(defn trim "Remove whitespace and leading @"
  [s]
  (nth (->> s .trim (re-find #"(^@?)(.+)"))
       2))

(def karma
  (partial core/match-first
           [[inc-match (fn [state packet [_ _ target]]
                         (let [source (source packet)
                               target (trim target)]
                           (if (= source target)
                             "You can't give karma to yourself."
                             (format "%s: %s" target (get (e/swap! state transfer-karma source target)
                                                          target)))))]
            [(partial core/command-args :karma)
             (fn [state packet args]
               (let [target (or (trim args) (source packet))]
                 (format "%s: %s" target (let [target-score (.get @state target)]
                                           (or target-score "No karma")))))]
            [dec-match (fn [_ _ target]
                         (format "Karma can only be given, not taken. But your displeasure with %s is duly noted." target))]]))

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
           (e/file-atom {} (format "%s/karma_db.clj" core/config-dir))))

(defmethod replbot.plugin/get-all (ns-name *ns*) [_]
  {:karma plugin})
