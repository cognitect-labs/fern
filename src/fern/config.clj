(ns fern.config
  "Flexible configuration in a Clojure map.")

(declare evaluate*)

(defn- eval-f [cfg shallow depth]
  (fn [x] (evaluate* x cfg shallow (inc depth))))

(defn evaluate-dispatch-f [x cfg shallow depth]
  (when (> depth 100)
    (throw 
      (ex-info
        (str "Runnaway evaluation recursion while evaluating [" x "].")
        {:cfg cfg :shallow shallow :depth depth})))

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

(defmethod evaluate* :symbol [x cfg shallow depth]
  (if shallow
    x
    (evaluate* (cfg x) cfg shallow (inc depth))))

(defmethod evaluate* :vector [x cfg shallow depth]
  (if shallow
    x
    (mapv (eval-f cfg shallow depth) x)))

(defmethod evaluate* :map [x cfg shallow depth]
  (if shallow
    x
    (zipmap (map (eval-f cfg shallow depth) (keys x))
            (map (eval-f cfg shallow depth) (vals x)))))

(defmethod evaluate* :list [x cfg shallow depth]
  (cond
    (= (first x) 'quote) (second x)
    shallow              (map (eval-f cfg shallow depth) x)
    :default             x))

(defmethod evaluate* :default [x _ _ _] x)
    

(defn evaluate 
  ([x cfg] (evaluate* x cfg false 0))
  ([x cfg shallow] (evaluate* x cfg shallow 0)))

