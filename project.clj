(defproject com.cognitect/fern "0.1.6-SNAPSHOT"
  :description  "Plain but useful language for data"
  :url          "https://github.com/cognitect-labs/fern"
  :license      {:name "Eclipse Public License"
                 :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1" :scope "provided"]
                 [org.clojure/tools.reader "1.3.0"]
                 [fipp "0.6.14"]
                 [mvxcvi/puget "1.1.2"]
                 [jline/jline "2.14.2"]]
  :profiles     {:dev {:dependencies [[org.clojure/tools.namespace "0.3.0-alpha3"]]
                       :source-paths ["dev"]}})
