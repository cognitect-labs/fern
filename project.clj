(defproject com.cognitect/fern "0.1.7-SNAPSHOT"
  :description  "Plain but useful language for data"
  :url          "https://github.com/cognitect-labs/fern"
  :license      {:name "Eclipse Public License"
                 :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.3" :scope "provided"]
                 [org.clojure/tools.reader "1.3.5"]
                 [fipp "0.6.23"]
                 [mvxcvi/puget "1.3.1"]
                 [jline/jline "2.14.2"]]
  :profiles     {:dev {:dependencies [[org.clojure/tools.namespace "1.1.0"]]
                       :source-paths ["dev"]}}
  :repositories [["releases" {:url "https://repo.clojars.org"
                              :creds :gpg}]])
