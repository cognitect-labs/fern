(ns fern
  "Flexible configuration in a Clojure map."
  (:require [clojure.string :as str]))

(defprotocol Evaluable
  (evaluate [this x]))

(declare do-evaluate)

(defn- make-exinfo
  ([x headline s history]
   (ex-info s {:headline headline :history history}))
  ([x headline s history cause]
   (ex-info (if cause (.getMessage cause) s) (merge {:history history :headline headline} (ex-data cause)) cause)))

(defn- evaluate* [x cfg history]
  (when (> (count history) 100)
    (throw (make-exinfo x "Runaway recursion" (str "Runaway recursion while evaluating [" x "].") history)))

  (let [new-history (conj history x)]
    (do-evaluate x cfg new-history)))

(defn- symbol-not-found
  [x history keys]
  (make-exinfo
   x
   (str "Cannot find '" x "' in the configuration.")
   (str "Cannot find '" x "' in the configuration. Available keys are " (str/join ", " (sort keys)))
   history))

(defn- error-while-evaluating
  [x history cause]
  (make-exinfo
   x
   "There was a problem during Clojure evaluation."
   (str "There was a problem during Clojure evaluation:\n" x "\n" (.getMessage cause))
   history
   cause))

(defn- assert-symbol-exists
  [symbol-table x history]
  (when-not (contains? symbol-table x)
    (throw (symbol-not-found x history (keys symbol-table)))))

(defn- add-meta [x md]
  (if (and md (instance? clojure.lang.IObj x))
    (with-meta x md)
    x))

(defn- copy-meta [src dst] (add-meta dst (meta src)))

(defn- eval-f [cfg history]
  (fn [x] (evaluate* x cfg history)))

(defn- listy? [x]
  (or (list? x) (instance? clojure.lang.Cons x)))

(defn- evaluate-dispatch-f [x cfg history]
  (cond
    (and (listy? x) (= (first x) 'quote)) :quote
    (and (listy? x) (= (first x) 'clojure.core/deref)) :deref
    (listy? x) :list
    (symbol? x) :identity
    (keyword? x) :identity
    (number? x) :identity
    (string? x) :identity
    (vector? x) :vector
    (record? x) (class x)
    (map? x) :map
    :default (class x)))

(defmulti ^:private do-evaluate evaluate-dispatch-f)

(defmethod do-evaluate :identity [x _ _] x)

(defn- deref-symbol [x cfg history]
  {:pre [(symbol? x)]}
  (if (= '*fern* x)
    cfg
    (do
      (assert-symbol-exists cfg x history)
      (let [cache  (.-cache cfg)
            result (if (contains? @cache x)
                     (get @cache x)
                     (evaluate* (get cfg x) cfg history))]
        (swap! cache assoc x result)
        result))))

(defmethod do-evaluate :vector [x cfg history]
  (copy-meta x
             (mapv (eval-f cfg history) x)))

(defmethod do-evaluate :map [x cfg history]
  (copy-meta x
             (zipmap (map (eval-f cfg history) (keys x))
                     (map (eval-f cfg history) (vals x)))))


(defmethod do-evaluate :list [x cfg history]
  (try
    (copy-meta x  (eval (map (eval-f cfg history) x)))
    (catch clojure.lang.Compiler$CompilerException ce
      (throw (error-while-evaluating x history (.getCause ce))))
    (catch Throwable t
      (throw (error-while-evaluating x history t)))))

(defmethod do-evaluate :quote [x cfg history]
  (second x))

(defmethod do-evaluate :deref [x cfg history]
  (deref-symbol (second x) cfg history))

(defmethod do-evaluate :default [x _ _] x)

(deftype Environment [symbol-table cache]
  Evaluable
  (evaluate [this x]
    (assert-symbol-exists symbol-table x [])
    (evaluate* (get symbol-table x) this [(list `deref x)]))

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

  clojure.lang.IPersistentMap
  (assocEx [this key val]
    (Environment. (.assocEx symbol-table key val) (atom {})))
  (without [this key]
    (Environment. (dissoc symbol-table key) (atom {})))
  (iterator [this]
    (.iterator symbol-table))
  (forEach [this action]
    (.forEach symbol-table action))
  (spliterator [this]
    (.spliterator symbol-table))

  Object
  (toString [_]
     (str "Env:" symbol-table)))

(defn environment
  [m]
  (->Environment m (atom {})))
