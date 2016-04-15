(ns replbot.plugins.help
  (:require [replbot.plugin :as plugin]
            [replbot.core :refer [config command match-first command-args]])
  (:import [replbot.plugin Plugin PluginDocs ActivationDocs]))

(def help 
  (partial match-first
           [[(partial command-args :help)
             (fn [_ _ args]
               (if (empty? args)
                 (apply str
                        (interpose "\n\n"
                                   (for [[_ plugin] @plugin/active]
                                     (format "%s: %s\n  Behaviors: [%s]"
                                             (-> plugin :docs :name)
                                             (-> plugin :docs :description)
                                             (apply str
                                                    (interpose ", "
                                                               (map (comp name :help-kw)
                                                                    (-> plugin :docs :activations))))))))
                 (let [all-activations (->> plugin/active deref vals (map :docs) (mapcat :activations))
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
                     (format "%s: %s" args "Behavior not found.")))))]]))

(def plugin (Plugin. #'help
                     (PluginDocs. "Help"
                                  "Documentation system that lists all the bot's behaviors and commands"
                                  [(ActivationDocs. :help [:help] "List all available plugins and their behaviors. Try @help and a behavior name.")
                                   (ActivationDocs. :help-cmd [:help :behavior] "Show documentation for 'behavior' - how it is activated, and what it does. You just did it.")])
                     nil))

(defmethod replbot.plugin/get-all (ns-name *ns*) [_]
  {:help plugin})
