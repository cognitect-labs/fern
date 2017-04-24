(ns fern
  "Flexible configuration in a Clojure map."
  (:require [clojure.string :as str]))

(defn- literal-dispatch-f [tag & _] tag)

(defmulti literal literal-dispatch-f)

(defmethod literal :default [ & args]
  (throw
    (ex-info
      (str "Undefined literal: '" (first args) "' used in expression " (cons 'lit args) ".")
      {})))

(defprotocol Evaluable
  (evaluate [this x]))

(declare evaluate*)

(defn- symbol-not-found
  [x keys]
  (str "Cannot find '" x "' in the configuration. Available keys are " (str/join ", " (sort keys))))

(defn- assert-symbol-exists
  [symbol-table x]
  (when-not (contains? symbol-table x)
    (throw (ex-info (symbol-not-found x (keys symbol-table)) {}))))


(defn- add-meta [x md]
  #_(println "add meta: " x " md: " md)
  (if (and md (instance? clojure.lang.IObj x))
    (with-meta x md)
    x))

(defn- copy-meta [src dst] (add-meta dst (meta src)))

(defn- eval-f [cfg depth]
  (fn [x] (evaluate* x cfg (inc depth))))

(defn- evaluate-dispatch-f [x cfg depth]
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

(defmethod evaluate* :number [x _ _] x)
(defmethod evaluate* :string [x _ _] x)
(defmethod evaluate* :keyword [x _ _] x)
(defmethod evaluate* :symbol [x _ _] x)

(defn deref-symbol [x cfg depth]
  {:pre [(symbol? x)]}
  (if (= '*fern* x)
    cfg
    (do
      (assert-symbol-exists cfg x)
      (let [cache  (.-cache cfg)
            result (if (contains? @cache x)
                     (get @cache x)
                     (evaluate* (get cfg x) cfg (inc depth)))]
        (swap! cache assoc x result)
        result))))

(defmethod evaluate* :vector [x cfg depth]
  (copy-meta x
             (mapv (eval-f cfg depth) x)))

(defmethod evaluate* :map [x cfg depth]
  (copy-meta x
             (zipmap (map (eval-f cfg depth) (keys x))
                     (map (eval-f cfg depth) (vals x)))))

(defmethod evaluate* :list [x cfg depth]
  (cond
    (= (first x) 'clojure.core/deref) (deref-symbol (second x) cfg depth)
    (= (first x) 'quote)              (second x)
    (= (first x) 'lit)                (copy-meta x
                                                 (apply literal (second x) (map (eval-f cfg depth) (drop 2 x))))
    :else                             (copy-meta x
                                                 (apply list (map (eval-f cfg depth) x)))))

(defmethod evaluate* :default [x _ _] x)

(deftype Environment [symbol-table cache]
  Evaluable
  (evaluate [this x]
    (assert-symbol-exists symbol-table x)
    (evaluate* (get symbol-table x) this 0))

  clojure.lang.Associative
  (containsKey [this k]
    (.containsKey symbol-table k))
  (entryAt [this k]
    (.entryAt symbol-table k))
  (assoc [this k v]
    (Environment. (assoc symbol-table k v) (atom {})))

  clojure.lang.Seqable
  (seq [this]
    (.seq symbol-table))

  clojure.lang.IPersistentCollection
  (count [this]
    (.count symbol-table))
  (empty [this]
    (Environment. {} (atom {})))
  (cons  [this obj]
    (Environment. (conj symbol-table obj) (atom {})))
  (equiv [this other]
    (and
      (instance? Environment other)
      (.equiv symbol-table (.-symbol-table ^Environment other))))

  clojure.lang.ILookup
  (valAt [this x]
    (.valAt symbol-table x))
  (valAt [this x not-found]
    (.valAt symbol-table x not-found))

  Object
  (toString [_]
     (str "Env:" symbol-table)))

(defn environment
  [m]
  (->Environment m (atom {})))
