(ns fern.either
  (:require [fern.printable :as printable]
            [fern.protocols :as p]))

;; Portions derived from https://github.com/funcool/cats
;; See docs/licenses/funcool/cats/LICENSE

(declare either)

;; ========================================
;; A "right" value is success. Computations will continue to use
;; "right" and produce new "right" values.
(deftype Right [v]
  p/Contextual
  (-get-context [_] either)

  p/Comonad
  (-extract [_] v)

  clojure.lang.IDeref
  (deref [_] v)

  clojure.lang.ILookup
  (valAt [this k]
    (if (= k :right) @this nil))
  (valAt [this k not-found]
    (if (= k :right) @this not-found))

  p/Printable
  (-repr [this]
    (str "#fern.either.right[" (pr-str v) "]"))

  Object
  (equals [self other]
    (if (instance? Right other)
      (= v (.-v other))
      false)))

;; Improve the readability of values printed at the repl
(printable/make-printable Right)

;; We don't want to expose the type itself.
(alter-meta! #'->Right assoc :private true)

;; Instead, we want callers to use this constructor.
(defn right
  "Right constructor"
  ([] (Right. nil))
  ([v] (Right. v)))

;; Type check predicate
(defn right?
  "True if `v` is an instance of the right type."
  [v]
  (instance? Right v))

;; ========================================
;; A "left" value is an error. No more computation will be done on the
;; left value. It stays the same.
(deftype Left [v]
  p/Contextual
  (-get-context [_] either)

  p/Comonad
  (-extract [_] v)

  clojure.lang.IDeref
  (deref [_] v)

  clojure.lang.ILookup
  (valAt [this k]
    (if (= k :left) @this nil))
  (valAt [this k not-found]
    (if (= k :left) @this not-found))

  p/Printable
  (-repr [this]
    (str "#fern.either.left[" (pr-str v) "]"))

  Object
  (equals [self other]
    (if (instance? Left other)
      (= v (.-v other))
      false)))

;; Improve the readability of values printed at the repl
(printable/make-printable Left)

;; We don't want callers to use this constructor either.
(alter-meta! #'->Left assoc :private true)

(defn left
  "Left constructor"
  ([] (Left. nil))
  ([v] (Left. v)))

(defn left?
  "True if `v` is an instance of the left type."
  [v]
  (instance? Left v))

;; ========================================
;; "either" is a value that kind of acts like a type.
(def ^{:no-doc true}
  either
  (reify
    p/Context
    p/Printable
    (-repr [_]
      "#fern.either.either{}")

    p/Functor
    (-fmap [_ f s]
      (if (right? s)
        (right (f (.-v s)))
        s))

    p/Applicative
    (-pure [_ v]
      (right v))

    (-fapply [m af av]
      (if (right? af)
        (p/-fmap m (.-v af) av)
        af))

    p/Monad
    (-mreturn [_ v]
      (right v))

    (-mbind [_ s f]
      (assert (either? s) (str "Context mismatch: " (p/-repr s)
                               " is not allowed to use with either context."))
      (if (right? s)
        (f (.-v ^Right s))
        s))))

(defn either?
  "True if `v` is either a left or right type."
  [v]
  (or (left? v) (right? v)))

;; ========================================
;; Functions building on 'either's.

(defn branch
  "Given an either and two functions; if the either is a left then
  apply the first function to the value it contains. Otherwise apply
  the second function to the value of the right"
  [e left-fn right-fn]
  {:pre [(either? e)]}
  (if (left? e)
    (left (left-fn (p/-extract e)))
    (right (right-fn (p/-extract e)))))

;; ========================================
;; Macro to conveniently turn exceptions into Left values.

(defmacro try-either
  "Try to evaluate the body and return the result as a right. Any
  exception will be caught and returned as a left."
  [& forms]
  `(try (right (do ~@forms)) (catch Throwable t# (left t#))))
