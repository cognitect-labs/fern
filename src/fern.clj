(ns fern
  "Flexible configuration in a Clojure map."
  (:require [clojure.string :as str]))

(defn- literal-dispatch-f [tag & _] tag)

(defmulti literal literal-dispatch-f)

(defprotocol Evaluable
  (evaluate [this x]))

(declare evaluate*)

(defn- add-meta [x md]
  #_(println "add meta: " x " md: " md)
  (if (and md (instance? clojure.lang.IObj x))
    (with-meta x md)
    x))

(defn- copy-meta [src dst] (add-meta dst (meta src)))

(defn- eval-f [cfg cache depth]
  (fn [x] (evaluate* x cfg cache (inc depth))))

(defn- evaluate-dispatch-f [x cfg cache depth]
  #_(println "evaling x: " x "cfg: " cfg "shallow: " "depth: " depth)
  (when (> depth 100)
    (throw
      (ex-info
        (str "Runnaway evaluation recursion while evaluating [" x "].")
        {:expression x :cfg cfg :depth depth})))

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

(defmethod evaluate* :number [x _ _ _] x)
(defmethod evaluate* :string [x _ _ _] x)
(defmethod evaluate* :keyword [x _ _ _] x)

(defmethod evaluate* :symbol [x cfg cache depth]
  (if-not (contains? cfg x)
    (throw (ex-info (str "Cannot find '" x "' in the configuration. Available keys are " (str/join ", " (sort (keys cfg)))) {}))
    (let [result (if (contains? @cache x)
                   (get @cache x)
                   (evaluate* (cfg x) cfg cache (inc depth)))]
      (swap! cache assoc x result)
      result)))

(defmethod evaluate* :vector [x cfg cache depth]
  (copy-meta x
             (mapv (eval-f cfg cache depth) x)))

(defmethod evaluate* :map [x cfg cache depth]
  (copy-meta x
             (zipmap (map (eval-f cfg cache depth) (keys x))
                     (map (eval-f cfg cache depth) (vals x)))))

(defmethod evaluate* :list [x cfg cache depth]
  (cond
    (= (first x) 'fern/quote) (second x)
    (= (first x) 'lit)        (copy-meta x
                                         (apply literal (second x) (map (eval-f cfg cache depth) (drop 2 x))))
    :else                     (copy-meta x
                                         (apply list (map (eval-f cfg cache depth) x)))))

(defmethod evaluate* :default [x _ _ _] x)

(deftype Environment [symbol-table cache]
  Evaluable
  (evaluate [this x]
    (evaluate* x symbol-table cache 0))

  clojure.lang.ILookup
  (valAt [this x]
    (get symbol-table x))
  (valAt [this x not-found]
    (get symbol-table x not-found))
    
  Object
  (toString [_]
     (str "Env:" symbol-table)))

(defn environment
  [m]
  (->Environment m (atom {})))
