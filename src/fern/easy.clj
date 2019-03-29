(ns fern.easy
  (:refer-clojure :exclude [load])
  (:require [fern :as f]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as rt]
            [fipp.visit :as fv]
            [fern.printer :as printer]
            [puget.color.ansi :as ansi])
  (:import (jline TerminalFactory)))

(defn string->environment
  "Read a Fern environment from a string or a reader.
  Use the file name (if supplied) in any exception messages.
  Only call this function with trusted data: This function
  uses clojure.tools.reader and can execute arbitrary code."
  ([s-or-reader] (string->environment s-or-reader nil))
  ([s-or-reader filename]
   (with-open [r (rt/indexing-push-back-reader s-or-reader)
               r (if filename
                   (rt/source-logging-push-back-reader r 1 filename)
                   r)]
     (f/environment (reader/read r)))))

(def reader->environment string->environment)

(defn file->environment
  "Read a fern environment from the file speficied by path.
  Only use this function to read trusted data: This function
  uses clojure.tools.reader and can execute arbitrary code."
  [path]
  (with-open [r (io/reader path)]
    (reader->environment r (str path))))

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
  "Deprecated. Use `load` and pass in a reader.

   Kept for backward compatibility."
  ([path] (load-environment path nil))
  ([path plugin-symbol]
   (cond-> (file->environment path)
     (not (nil? plugin-symbol))
     (load-plugin-namespaces! plugin-symbol))))

(defn load
  ([reader path] (load reader path []))
  ([reader path plugin-symbols]
   (let [env (reader->environment reader path)]
     (doseq [plugin plugin-symbols]
       (load-plugin-namespaces! env plugin))
     env)))

(defn validate!
 "Resolve all of the keys in the environment.
  Ensures that all of the keys will work but
  will trigger any side effects hidden in values."
 [env]
 (doseq [k (keys env)]
   (f/evaluate env k)))

(def underef-handlers
  {clojure.lang.Cons
   (fn cons-handler
     [printer value]
     (fv/visit-seq printer value))

   clojure.lang.ISeq
   (fn iseq-handler
     [printer value]
     (fv/visit-seq printer value))})

(defn- pprint-expr [e]
  (let [l  (some-> e meta :line)
        l' (if l (format "%d:\t" l) "\t")
        v  (printer/cprint-str e {:seq-limit 5 :print-handlers underef-handlers})
        v  (str/replace v #"\n" "\n\t")]
    (str l' v \newline)))

(defn print-evaluation-history [file h]
  (print "\nI got here by evaluating these, from most recent to oldest:\n\n")
  (when file
    (println (ansi/sgr file :red)))
  (print
   (str/join (map pprint-expr (reverse h)))))

(defn terminal-width [t]
  (.getWidth t))

(defn hline
  ([w s]
   (let [pad (- w 4 (count s))]
     (str  "---" s  (str/join (repeat pad "-")) \newline)))
  ([w l r]
   (if (empty? r)
     (hline w l)
     (let [pad (- w 6 (count l) (count r))]
       (str  "---" l (str/join (repeat pad "-")) " " r \newline)))))

(defn abbreviate-left
  [w s]
  (if (>= w (count s))
    s
    (str " ..." (subs s (- (count s) w -4) (count s)))))

(defn abbreviate
  [w s]
  (if (>= w (count s))
    s
    (str (subs s 0 (- w 5)) " ...")))

(defn print-in-width [w s]
  (when s
    (doseq [line (str/split s #"\n")]
      (println (abbreviate w line)))))

(defn print-evaluation-exception
  ([e]
   (print-evaluation-exception e nil))
  ([e filename]
   (let [t    (TerminalFactory/get)
         w    (terminal-width t)
         h    (some-> e ex-data :history)
         file (or (some-> h last meta :file) filename)]
     (println (ansi/sgr (hline w " ERROR " (abbreviate-left 35 file)) :red))
     (println)
     (print-in-width w (:headline (ex-data e)))
     (print-in-width w (.getMessage e))
     (when h
       (print-evaluation-history file h)))))

(defn print-other-exception
  ([e]
   (print-other-exception e nil))
  ([e file]
   (let [t    (TerminalFactory/get)
         w    (terminal-width t)
         msg  (.getMessage e)
         msg  (if (empty? msg) (.getName (.getClass e)) msg)]
     (println (ansi/sgr (hline w " ERROR " (abbreviate-left 35 file)) :red))
     (println)
     (println msg))))

(defn print-error-message
  ([headline]
   (print-error-message headline nil))
  ([headline file]
   (let [t    (TerminalFactory/get)
         w    (terminal-width t)]
     (println (ansi/sgr (hline w " ERROR " (abbreviate-left 35 file)) :red))
     (println)
     (println headline))))
