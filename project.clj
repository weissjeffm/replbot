(defproject replbot "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main replbot.core
  :dependencies [[org.clojure/clojure "1.8.0"]
                 ;;[quit-yo-jibber "0.6.0"]
                 [org.igniterealtime.smack/smack-core "4.1.6"]
                 [org.igniterealtime.smack/smack-tcp "4.1.6"]
                 [org.igniterealtime.smack/smack-extensions "4.1.6"]
                 [org.igniterealtime.smack/smack-java7 "4.1.6"]
                 [org.igniterealtime.smack/smack-debug "4.1.6"]
                 [clojail "1.0.4"]
                 [ororo "0.1.0"]])
