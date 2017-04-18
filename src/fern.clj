(ns fern
  "Flexible configuration in a Clojure map."
  (:require [clojure.string :as str]))

(declare evaluate*)

(defn add-meta [x md]
  #_(println "add meta: " x " md: " md)
  (if (and md (instance? clojure.lang.IObj x))
    (with-meta x md)
    x))

(defn copy-meta [src dst] (add-meta dst (meta src)))

(defn- eval-f [cfg depth]
  (fn [x] (evaluate* x cfg (inc depth))))

(defn evaluate-dispatch-f [x cfg depth]
  #_(println "evaling x: " x "cfg: " cfg "shallow: " "depth: " depth)
  (when (> depth 100)
    (throw
      (ex-info
        (str "Runnaway evaluation recursion while evaluating [" x "].")
        {:cfg cfg :depth depth})))

  (cond
    (record? x) (class x)
    (map? x) :map
    (symbol? x) :symbol
    (keyword? x) :keyword
    (vector? x) :vector
    (number? x) :number
    (string? x) :string
    (list? x) :list
    :default (class x)))

(defmulti evaluate* evaluate-dispatch-f)

(defmethod evaluate* :number [x _ _] x)
(defmethod evaluate* :string [x _ _] x)
(defmethod evaluate* :keyword [x _ _] x)

(defmethod evaluate* :symbol [x cfg depth]
  (if-not (contains? cfg x)
    (throw (ex-info (str "Cannot find '" x "' in the configuration. Available keys are " (str/join ", " (sort (keys cfg)))) {}))
    (copy-meta x
               (evaluate* (cfg x) cfg (inc depth)))))

(defmethod evaluate* :vector [x cfg depth]
  (copy-meta x
             (mapv (eval-f cfg depth) x)))

(defmethod evaluate* :map [x cfg depth]
  (copy-meta x
             (zipmap (map (eval-f cfg depth) (keys x))
                     (map (eval-f cfg depth) (vals x)))))

(defmethod evaluate* :list [x cfg depth]
  (if  (= (first x) 'quote)
    (second x)
    (copy-meta x
               (apply list (map (eval-f cfg depth) )))))

(defmethod evaluate* :default [x _ _] x)


(defn evaluate [x cfg] (evaluate* x cfg 0))

(defn lookup [x cfg]
  (get cfg x))
