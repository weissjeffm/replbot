(ns replbot.xmpp.presence
  (:import [org.jivesoftware.smack.roster RosterListener]
           [org.jxmpp.util XmppStringUtils]
           [org.jivesoftware.smack.roster Roster]
           [org.jivesoftware.smack.packet Presence Presence$Type Presence$Mode]))

;; TODO Check presence types match up with :available etc.
(defn mapify-presence [#^Presence m]
  {:from    (XmppStringUtils/parseBareJid (.getFrom m))
   :jid     (.getFrom m)
   :status  (.getStatus m)
   :type    (keyword (str (.getType m)))
   :mode    (keyword (str (.getMode m)))
   :online? (.isAvailable m)
   :away?   (.isAway m)})

(defn with-presence-map [f]
  (fn [presence] (mapify-presence (f presence))))

(def presence-types {:available   Presence$Type/available
                     :unavailable Presence$Type/unavailable})

(def presence-modes {:available Presence$Mode/available
                     :away Presence$Mode/away
                     :chat Presence$Mode/chat
                     :dnd Presence$Mode/dnd
                     :xa Presence$Mode/xa})
(defn set-availability!
  [conn type & [status addr mode]]
  (let [packet (Presence. (type presence-types))]
    (when status
      (doto packet (.setStatus status)))
    (when addr
      (doto packet (.setTo addr)))
    (when mode
      (doto packet (.setMode (presence-modes mode))))
    (doto conn (.sendPacket packet))))

(defn add-presence-listener [conn f]
  (.addRosterListener (Roster/getInstanceFor conn)
                      (proxy [RosterListener] []
                        (entriesAdded [_])
                        (entriesDeleted [_])
                        (entriesUpdated [_])
                        (presenceChanged [presence]
                          (f conn (mapify-presence presence)))))
  conn)

(defn subscribe-presence [conn addr]
  (let [presence (Presence. Presence$Type/subscribe)]
    (.setTo presence addr)
    (doto conn (.sendPacket presence))))


(defn- all-jids-for-user
  [conn user]
  (map (memfn getFrom) (iterator-seq (.getPresences (Roster/getInstanceFor conn) user))))

(def ^:private resource-id {:phone   ["Messaging" "android_talk"]
                            :desktop ["messaging-smgmail" "messaging-AChromeExtension"
                                      "gmail" "BitlBee" "Kopete" "Adium"]})

(defn- resource-type? [conn typeof user]
  (re-matches (re-pattern (str ".*?/(" (clojure.string/join "|" (typeof resource-id)) ").*?")) user))

(defn all-jids-for-user-of-type [conn typeof user]
  (seq (filter (partial resource-type? conn typeof) (all-jids-for-user conn user))))
