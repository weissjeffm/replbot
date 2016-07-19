(ns replbot.xmpp.muc
  (:require [replbot.xmpp.message :as message]
            [replbot.xmpp.presence :as presence])
  (:import [org.jivesoftware.smackx.muc MultiUserChatManager InvitationListener DiscussionHistory]
           [org.jxmpp.util XmppStringUtils]
           [org.jivesoftware.smackx.iqregister AccountManager]
           [org.jivesoftware.smack MessageListener PresenceListener]
           [org.jivesoftware.smack.filter StanzaTypeFilter
            AndFilter
            PacketFilter]
           [org.jivesoftware.smack.packet Presence$Mode]))

(def ^:dynamic *room-nickname*)
(defn muc
  "Returns the MultiUserChat for the given connection and room"
  [conn room]
  (-> conn MultiUserChatManager/getInstanceFor (.getMultiUserChat room)))

(defn join
  ([conn room room-nickname password discussion-history timeout]
   (join (muc conn room) room-nickname password discussion-history timeout))
  ([muc room-nickname password discussion-history timeout]
   (.join muc
          room-nickname
          password
          (or discussion-history
              (doto (DiscussionHistory.)
                (.setMaxStanzas 0)))
          (or timeout 5000))))

(defn leave
  ([conn room]
   (.leave (muc conn room)))
  ([muc]
   (.leave muc)))

(defn respond-listener
  "Takes the MultiUserChat object, pred, and a function f. Passes message
   through pred, if true, replies with (f msg)"
  [muc pred f]
  (reify MessageListener
    (processMessage [this msg]
      (let [msg (message/message-map msg)]
        (when (pred msg)
          (when-let [reply (f msg)]
            (.sendMessage muc reply)))))))

(defn presence-listener
  [pred f]
  (reify PresenceListener
    (processPresence [this pres]
      (let [pres (presence/mapify-presence pres)]
        (when (pred pres)
          (f pres))))))

(defn simple-reply [msg]
  (str "You said " (:body msg)))

(defn my-name-first? [name msg]
  (-> msg :body (clojure.string/starts-with? name)))

(defn invitation-listener [conn f]
  (reify InvitationListener
    (invitationReceived [this conn room inviter reason password message]
      (f conn room inviter reason password message))))

(defn always-join [room-nickname conn room _ _ password _]
  (join conn room room-nickname password nil nil))

(defn add-invitation-listener
  [conn f]
  (.addInvitationListener (MultiUserChatManager/getInstanceFor conn)
                          (invitation-listener conn f)))



(defn set-availability!
  [conn muc status type]
  (presence/set-availability! conn type status (.getRoom muc)))
