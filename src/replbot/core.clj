(ns replbot.core
  (:require [quit-yo-jibber :as xmpp]
            [quit-yo-jibber.muc :as xmpp-muc]
            [quit-yo-jibber.presence :as xmpp-presence])
  (:import [org.jxmpp.util XmppStringUtils]))


(def connect-info {:username "549968_3678804@chat.hipchat.com"
                   :password "111111jm"
                   :host "chat.hipchat.com"
                   :port 5222
                   :domain "chat.hipchat.com"
                   :ping-interval 30
                   :nick "ReplBot"})

(def config {:command-prefix "@"
             :eval-prefix ","
             :data-dir "/home/jweiss/.replbot/db/"
             :hipchat-mode true})

(defn command
  "Returns command keyword and args if the message is a command, otherwise nil"
  [message]
  (let [cmd-re (re-pattern (format "^%s([a-zA-Z0-9-]+)(.*)" (:command-prefix config)))
        [_ command args] (some->> message :body .trim (re-find cmd-re))]
    (when command [(keyword command) (.trim args)])))

(defrecord ActivationDocs [help-kw usage description])
(defrecord PluginDocs [name description activations])
(defrecord Plugin [packet-fn docs state])

(def plugin-library (atom {}))
(def active-plugins (atom {}))

(defn add-plugin-to-library [kw plugin]
  (swap! plugin-library assoc kw plugin))

(defn activate-plugin [kw plugin]
  (swap! active-plugins assoc kw plugin))

(defn message-dispatch [packet]
  (println "dispatching!!" packet)
  ;; take first non-nil
  (let [plugins (vals @active-plugins)
        fs (for [plugin plugins]
             (fn [packet]
               ((:packet-fn plugin) (:state plugin) packet)))]
    ;;(println "calling" fs)
    (let [res (first (filter identity ((apply juxt fs) packet)))]
      ;;(println "got" res)
      res)))

(defn not-from-me [message]
  (-> message :from XmppStringUtils/parseResource (not= (connect-info :nick))))

(defn on-invitation [conn room inviter reason password message]
  (println "received invite to " conn room inviter reason password message)
  (xmpp-muc/join room (:nick connect-info) password nil nil)
  (.addMessageListener room (xmpp-muc/respond-listener room
                                                       #'not-from-me
                                                       #'message-dispatch)))
(defn start-bot []
  (let [myconn (xmpp/make-connection connect-info)]
    (xmpp-muc/add-invitation-listener myconn #'on-invitation)))


(comment
  ;; reset plugins
  ;;
  (reset! active-plugins {:help help-plugin, :eval eval-plugin, :karma karma-plugin})
  )
