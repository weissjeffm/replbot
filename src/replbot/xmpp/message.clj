(ns replbot.xmpp.message
  (:import [org.jivesoftware.smack        StanzaListener
                                          XMPPException]
           [org.jivesoftware.smack.packet Message
                                          Message$Type]
           [org.jxmpp.util XmppStringUtils]
           [org.jivesoftware.smack.filter StanzaTypeFilter
                                          AndFilter
                                          PacketFilter]))

;; Stores a map of connections to lists of pending question callbacks
(def responses (atom {}))

(defn error-map [^Message m]
  (let [e (.getError m)]
    {:from  (.getFrom m)
     :to    (.getTo m)
     :body  (.getBody m)
     :error (.getMessage e)
     :code  (.getCode e)}))

(defn message-map [#^Message m]
  {:from    (.getFrom m)
   :to      (.getTo m)
   :subject (.getSubject m)
   :body    (.getBody m)
   :thread  (.getThread m)
   :jid     (.getFrom m)})

(defn create-message [to message-body]
  (doto (Message.)
    (.setTo to)
    (.setBody (str message-body))
    (.setType Message$Type/chat)))

(defn send-message [conn to message-body]
  (.sendPacket conn (create-message to message-body)))

;; Wrap a call to an answering function in another function that will
;; mark the question as answered on the responses atom, allowing other
;; messages through
(defn answer [f]
  (fn [conn message]
    (swap! responses update-in [conn] dissoc (:from message))
    (f conn message)))

(defn send-question [conn to message-body callback]
  (swap! responses assoc-in [conn to] (answer callback))
  (send-message conn to message-body))

;; Return a function which will either call the provided default handler
;; or the handler stored for a given user if currently awaiting a
;; response
(defn with-responder [default-handler]
  (fn [conn message]
    (let [handler  (or (get-in @responses [conn (:from message)])
                       default-handler)
          response (handler conn message)]
      (send-message conn (:from message) response))))

(defn awaiting-response? [conn from]
  (boolean (get-in @responses [conn from])))

(comment "Experimental"
 (defn on-no-pending-responses [conn f]
   (add-watch responses :empty
              (fn [_ _ _ updated-responses]
                (when (empty? (get updated-responses conn))
                  (f conn))))))

(defn with-message-map [handler]
  (fn [conn packet]
    (let [message (message-map #^Message packet)]
      (handler conn message))))

(defn add-message-listener [conn f]
  (doto conn
    (.addPacketListener
     (reify StanzaListener
       (processPacket [this packet]
         ((with-message-map (with-responder f)) conn packet)))
     (doto (AndFilter.)
       (.addFilter (StanzaTypeFilter. Message))
       (.addFilter (reify PacketFilter
                     (accept [this p]
                       (boolean (.getBody #^Message p)))))))))
