(ns replbot.core
  (:require [replbot.xmpp.core :as xmpp]
            [replbot.xmpp.muc :as xmpp-muc]
            [replbot.xmpp.presence :as xmpp-presence]
            [replbot.plugin :as plugin])
  (:import [org.jxmpp.util XmppStringUtils]))

(declare ^:dynamic config)

(defn command
  "Returns command keyword and args if the message is a command, otherwise nil"
  [message]
  (let [cmd-re (re-pattern (format "^%s([a-zA-Z0-9-]+)(.*)" (:command-prefix config)))
        [_ command args] (some->> message :body .trim (re-find cmd-re))]
    (when command [(keyword command) (.trim args)])))

(defn match-first
  "Takes a list of behaviors, where each is a pair: a predicate that
   operates on the incoming packet and returns nil if no action is to
   be taken, and a function of the packet and whatever the predicate
   returns. Calls the function if the predicate returns
   non-nil. Iterates over the list of behaviors and quits after the
   first matching predicate. Returns whatever the first matching
   function returns."
  [behaviors state packet]
  (first (drop-while nil? (for [[pred f] behaviors]
                            (let [pred-result (pred state packet)]
                              (when pred-result (f state packet pred-result)))))))

(defn command-args
  "If packet matches cmd (a keyword command), return the args (as string)"
  [cmd _ packet]
  (when-let [[c args] (command packet)]
    (when (= c cmd)
      args)))

(defn message-dispatch [packet]
  (println "dispatching!!" packet)
  ;; take first non-nil
  (let [plugins (vals @plugin/active)
        fs (for [plugin plugins]
             (fn [packet]
               ((:packet-fn plugin) (:state plugin) packet)))]
    ;;(println "calling" fs)
    (try (let [res (first (filter identity ((apply juxt fs) packet)))]
           res)
         (catch Throwable t (str "oops: " (pr-str t))))))

(defn not-from-me [message]
  (-> message :from XmppStringUtils/parseResource (not= (-> config :connection :nick))))

(defn on-invitation [conn room inviter reason password message]
  (println "received invite to " conn room inviter reason password message)
  (xmpp-muc/join room (-> config :connection :nick) password nil nil)
  (.addMessageListener room (xmpp-muc/respond-listener room
                                                       #'not-from-me
                                                       #'message-dispatch)))

(defn start-bot []
  (def config (read-string (slurp (str (System/getProperty "user.home") "/.replbot/config.clj"))))
  (plugin/load (:load-plugins config))
  (reset! plugin/active @plugin/library)
  (let [myconn (xmpp/make-connection (config :connection))]
    (xmpp-muc/add-invitation-listener myconn #'on-invitation)
    myconn))

(defn -main [& args]
  (start-bot))
