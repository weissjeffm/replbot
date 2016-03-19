(ns replbot.core
  (:require [quit-yo-jibber :as xmpp]
            [quit-yo-jibber.muc :as xmpp-muc]
            [quit-yo-jibber.presence :as xmpp-presence]
            [clojail.core    :refer [sandbox safe-read]]
            [clojail.testers :refer [secure-tester secure-tester-without-def]]
            [clojure.string :refer [starts-with?]])
  (:import [java.util.concurrent ExecutionException]
           [java.io StringWriter]
           [org.jxmpp.util XmppStringUtils]))


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

(defn eval? [packet]
  (starts-with? (:body packet) (:eval-prefix config)))

(defrecord ActivationDocs [help-kw usage description])
(defrecord PluginDocs [name description activations])

(defrecord Plugin [packet-fn docs state])

(def plugin-library (atom {}))
(def active-plugins (atom {}))

(defn help [_ packet]
  (when-let [[command args] (command packet)]
    (when (= command :help)
      (if (empty? args)
        (apply str
               (interpose "\n\n"
                          (for [[_ plugin] @active-plugins]
                            (format "%s: %s\n  Behaviors: [%s]"
                                    (-> plugin :docs :name)
                                    (-> plugin :docs :description)
                                    (apply str
                                           (interpose ", "
                                                      (map (comp name :help-kw)
                                                           (-> plugin :docs :activations))))))))
        (let [all-activations (->> active-plugins deref vals (map :docs) (mapcat :activations))
              by-kw (into {} (for [activation all-activations]
                               [(:help-kw activation) activation]))
              activation (-> args .trim keyword by-kw)]
          (if activation
            (format "%s: %s"
                    (let [usage (-> activation :usage)]
                      (cond (vector? usage)
                            (apply str (:command-prefix config) (-> usage first name)
                                   " " (interpose " " (map name (rest usage))))

                            (fn? usage)
                            (usage)

                            :else usage))
                    (:description activation))
            (format "%s: %s" args "Behavior not found.")))))))

(def help-plugin (Plugin. #'help
                          (PluginDocs. "Help"
                                       "Documentation system that lists all the bot's behaviors and commands"
                                       [(ActivationDocs. :help [:help] "List all available plugins and their behaviors. Try @help and a behavior name.")
                                        (ActivationDocs. :help-cmd [:help :behavior] "Show documentation for 'behavior' - how it is activated, and what it does. You just did it.")])
                          nil))

(defn add-plugin-to-library [kw plugin]
  (swap! plugin-library assoc kw plugin))

(defn activate-plugin [kw plugin]
  (swap! active-plugins assoc kw plugin))

(defn make-safe-eval
  [{sandbox-config :sandbox}]
  (let [our-sandbox (sandbox secure-tester-without-def)
        history (atom [nil nil nil])
        last-exception (atom nil)]
    (fn [form output]
      (binding [*out* output]
        (try
          (let [bindings {#'*print-length* 30
                          #'*1 (nth @history 0)
                          #'*2 (nth @history 1)
                          #'*3 (nth @history 2)
                          #'*e @last-exception
                          #'*out* output
                          #'*err* output}
                result (our-sandbox form bindings)]
            (swap! history (constantly [result (nth @history 0) (nth @history 1)]))
            (when (:hipchat-mode config)
              (print "/code "))
            (pr result))
          (catch ExecutionException e
            (swap! last-exception (constantly (.getCause e)))
            (print (.getMessage e)))
          (catch Throwable t
            (swap! last-exception (constantly t))
            (print (.getMessage t))))))))

(let [safe-eval (make-safe-eval {})]
  (defn clj-eval [_ packet]
    (when (eval? packet)
      (let [output (StringWriter.)]
        (safe-eval (safe-read (.substring (:body packet) 1)) output)
        (let [text (.toString output)]
          (println "eval result: " text)
          text)))))

(def eval-plugin (Plugin. #'clj-eval
                          (PluginDocs. "Evaluate Clojure Expression"
                                       "Evaluates a clojure expression in a sandbox environment."
                                       [(ActivationDocs. :eval
                                                         #(format "%s(expression)"
                                                                  (:eval-prefix config))
                                                         "Evaluate (expression)")])
                          nil))

(defn karma [state packet]
  (if-let [[_ _ target] (or (re-find #"^\((inc)\s+(.+)\)" (:body packet))
                            (re-find #"^([Tt]hanks,?|[Tt]hank you,?)\s+(.+)" (:body packet)))]
    (let [source (-> packet :from XmppStringUtils/parseResource)
          target (.trim target)
          inc (fnil inc 0)
          dec (fnil dec 0)
          transfer-karma (fn [karma-map source target]
                           (-> karma-map
                               (update-in [target] inc)
                               (update-in [source] dec)))]
      (swap! state transfer-karma source target)
      (format "%s: %s" target (-> state deref (get target))))
    (if-let [[command args] (command packet)]
      (when (= command :karma)
        (let [target (.trim args) ]
          (format "%s: %s" target (let [target-score (.get @state target)]
                                    (or target-score "No karma")))))
      (when-let [[_ _ target] (re-find #"^\((dec)\s+(.+)\)" (:body packet))]
       (format "Karma can only be given, not taken. But your displeasure with %s is duly noted." target)))))

(def karma-plugin
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
