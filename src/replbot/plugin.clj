(ns replbot.plugin
  (:refer-clojure :exclude [load]))

(defmulti get-all identity)

(defrecord ActivationDocs [help-kw usage description])
(defrecord PluginDocs [name description activations])
(defrecord Plugin [packet-fn docs state])

(def library (atom {}))
(def active (atom {}))

(defn load [ns-kws]
  (doseq [ns-kw ns-kws]
    (require ns-kw)
    (swap! library merge (get-all ns-kw))))












