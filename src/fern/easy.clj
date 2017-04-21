(ns fern.easy
  (:require [fern :as f]
            [clojure.java.io :as io]
            [clojure.tools.reader :as r]
            [clojure.tools.reader.reader-types :as rt]))

(defn string->environment
  [s]
  (f/environment (r/read (rt/indexing-push-back-reader s))))

(def reader->environment string->environment)

(defn file->environment
  [path]
  (with-open [r (io/reader path)]
    (reader->environment r)))

(defn load-plugin [pi]
  (try
    (require pi :reload)
    (catch Throwable t
      (let [msg (str "Error while loading plugin '" pi "': " (.getMessage t))]
        (throw (ex-info msg {:plugin pi} t))))))

(defn load-plugin-namespaces [env plugin-symbol]
  (let [plugins (f/evaluate env plugin-symbol)]
    (doseq [pi plugins] (load-plugin pi))
    env))

(defn load-environment
  ([path] (load-environment path nil))
  ([path plugin-symbol]
   (cond-> (file->environment path)
     (not (nil? plugin-symbol))
     (load-plugin-namespaces plugin-symbol))))
