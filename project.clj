(defproject com.cognitect/fern "0.1.0-SNAPSHOT"
  :description  "Plain but useful language for data"
  :url          "https://github.com/cognitect-labs/fern"
  :license      {:name "Eclipse Public License"
                 :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha15" :scope "provided"]
                 [org.clojure/tools.reader "1.0.0-beta4"]
                 [instaparse "1.4.5"]]
  :profiles     {:dev {:dependencies [[org.clojure/tools.namespace "0.3.0-alpha3"]]
                       :source-paths ["dev"]}})
