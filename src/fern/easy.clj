(ns fern.easy
  (:require [fern :as f]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as rt]))

(defn string->environment
  ([s-or-reader] (string->environment s-or-reader nil))
  ([s-or-reader filename]
   (with-open [r (rt/indexing-push-back-reader s-or-reader)
               r (if filename
                   (rt/source-logging-push-back-reader r 1 filename)
                   r)]
     (f/environment (reader/read r)))))

(def reader->environment string->environment)

(defn file->environment
  [path]
  (with-open [r (io/reader path)]
    (reader->environment r path)))

(defn load-plugin! [pi]
  (try
    (require pi :reload)
    (catch Throwable t
      (let [msg (str "Error while loading plugin '" pi "': " (.getMessage t))]
        (throw (ex-info msg {:plugin pi} t))))))

(defn load-plugin-namespaces! [env plugin-symbol]
  (when (contains? env plugin-symbol)
    (let [plugins (f/evaluate env plugin-symbol)]
      (doseq [pi plugins] (load-plugin! pi))))
  env)

(defn load-environment
  ([path] (load-environment path nil))
  ([path plugin-symbol]
   (cond-> (file->environment path)
     (not (nil? plugin-symbol))
     (load-plugin-namespaces! plugin-symbol))))

(defn validate!
 "Resolve all of the keys in the environment.
  Ensures that all of the keys will work but
  will trigger any side effects hidden in values."
 [env]
 (doseq [k (keys env)]
   (f/evaluate env k)))


(defn- pprint-expr [e]
  (with-out-str
    (pprint e)))

(defn print-evaluation-history [h]
  (print "\nI got here by evaluating these, from most recent to oldest:\n\n\t")
  (println (str/join "\t" (map pprint-expr (reverse h)))))

(defn print-evaluation-exception [e]
  (println "Error in evaluation:" (.getMessage e))
  (print-evaluation-history (:history (ex-data e))))

