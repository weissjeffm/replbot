(ns replbot.core
  (:require [quit-yo-jibber :as xmpp]
            [quit-yo-jibber.muc :as xmpp-muc]
            [quit-yo-jibber.presence :as xmpp-presence]
            [replbot.plugin :as plugin])
  (:import [org.jxmpp.util XmppStringUtils]))

(def config {:connection {:username "549968_3678804@chat.hipchat.com"
                          :password "111111jm"
                          :host "chat.hipchat.com"
                          :port 5222
                          :domain "chat.hipchat.com"
                          :ping-interval 30
                          :nick "ReplBot"}
             :command-prefix "@"

             :data-dir "/home/jweiss/.replbot/db/"
             :hipchat-mode true
             :load-plugins ['replbot.plugins.eval
                            'replbot.plugins.help
                            'replbot.plugins.karma]
             :plugins {:eval {:prefix ","}}})

(defn command
  "Returns command keyword and args if the message is a command, otherwise nil"
  [message]
  (let [cmd-re (re-pattern (format "^%s([a-zA-Z0-9-]+)(.*)" (:command-prefix config)))
        [_ command args] (some->> message :body .trim (re-find cmd-re))]
    (when command [(keyword command) (.trim args)])))

(defn message-dispatch [packet]
  (println "dispatching!!" packet)
  ;; take first non-nil
  (let [plugins (vals @plugin/active)
        fs (for [plugin plugins]
             (fn [packet]
               ((:packet-fn plugin) (:state plugin) packet)))]
    ;;(println "calling" fs)
    (let [res (first (filter identity ((apply juxt fs) packet)))]
      ;;(println "got" res)
      res)))

(defn not-from-me [message]
  (-> message :from XmppStringUtils/parseResource (not= (-> config :connection :nick))))

(defn on-invitation [conn room inviter reason password message]
  (println "received invite to " conn room inviter reason password message)
  (xmpp-muc/join room (-> config :connection :nick) password nil nil)
  (.addMessageListener room (xmpp-muc/respond-listener room
                                                       #'not-from-me
                                                       #'message-dispatch)))
(defn start-bot []
  (plugin/load (:load-plugins config))
  (reset! plugin/active @plugin/library)
  (let [myconn (xmpp/make-connection (config :connection))]
    (xmpp-muc/add-invitation-listener myconn #'on-invitation)))
