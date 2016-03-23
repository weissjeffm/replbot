(ns replbot.plugins.eval
  (:require [replbot.core :refer [config]]
            [replbot.plugin :as plugin]
            [clojail.core    :refer [sandbox safe-read]]
            [clojail.testers :refer [secure-tester secure-tester-without-def]]
            [clojure.string :refer [starts-with?]])
  (:import [replbot.plugin Plugin PluginDocs ActivationDocs]
           [java.io StringWriter]
           [java.util.concurrent ExecutionException]))

(defn eval? [packet]
  (starts-with? (:body packet) (-> config :plugins :eval :prefix)))

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
        (.toString output)))))

(def plugin (Plugin. #'clj-eval
                     (PluginDocs. "Evaluate Clojure Expression"
                                  "Evaluates a clojure expression in a sandbox environment."
                                  [(ActivationDocs. :eval
                                                    #(format "%s(expression)"
                                                             (-> config :plugins :eval :prefix))
                                                    "Evaluate (expression)")])
                     nil))

(defmethod plugin/get-all (ns-name *ns*) [_]
  {:eval #'plugin})
