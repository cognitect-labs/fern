(ns fern.config
  "Flexible configuration in a Clojure map.")

(declare evaluate*)

(defn- eval-f [cfg deep depth]
  (fn [x] (evaluate* x cfg deep (inc depth))))

(defn evaluate-dispatch-f [x cfg deep depth]
  (when (> depth 100)
    (throw 
      (ex-info
        (str "Runnaway evaluation recursion while evaluating [" x "].")
        {:cfg cfg :deep deep :depth depth})))

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

(defmethod evaluate* :symbol [x cfg deep depth]
  (if-not deep
    x
    (evaluate* (cfg x) cfg deep (inc depth))))

(defmethod evaluate* :vector [x cfg deep depth]
  (if-not deep
    x
    (mapv (eval-f cfg deep depth) x)))

(defmethod evaluate* :map [x cfg deep depth]
  (if-not deep
    x
    (zipmap (map (eval-f cfg deep depth) (keys x))
            (map (eval-f cfg deep depth) (vals x)))))

(defmethod evaluate* :list [x cfg deep depth]
  (cond
    (= (first x) 'quote) (second x)
    deep                 (map (eval-f cfg deep depth) x)
    :default             x))

(defmethod evaluate* :default [x _ _ _] x)
    

(defn evaluate 
  ([x cfg] (evaluate* x cfg true 0))
  ([x cfg deep] (evaluate* x cfg deep 0)))

