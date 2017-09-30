(defproject com.cognitect/fern "0.1.3"
  :description  "Plain but useful language for data"
  :url          "https://github.com/cognitect-labs/fern"
  :license      {:name "Eclipse Public License"
                 :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha15" :scope "provided"]
                 [org.clojure/tools.reader "1.0.0-beta4"]
                 [fipp "0.6.8"]
                 [mvxcvi/puget "1.0.1"]
                 [jline/jline "2.14.2"]]
  :profiles     {:dev {:dependencies [[org.clojure/tools.namespace "0.3.0-alpha3"]]
                       :source-paths ["dev"]}})
