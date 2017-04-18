;; Derived in part from https://github.com/funcool/cats
;; See docs/licenses/funcool/cats/LICENSE

;; Copyright (c) 2014-2016 Andrey Antukh <niwi@niwi.nz>
;; Copyright (c) 2014-2016 Alejandro Gómez <alejandro@dialelo.com>
;; All rights reserved.
;;
;; Redistribution and use in source and binary forms, with or without
;; modification, are permitted provided that the following conditions
;; are met:
;;
;; 1. Redistributions of source code must retain the above copyright
;;    notice, this list of conditions and the following disclaimer.
;; 2. Redistributions in binary form must reproduce the above copyright
;;    notice, this list of conditions and the following disclaimer in the
;;    documentation and/or other materials provided with the distribution.
;;
;; THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
;; IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
;; OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
;; IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
;; INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
;; NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
;; DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
;; THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
;; (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
;; THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

(ns fern.cats
  (:refer-clojure :exclude [when])
  (:require [fern.context :as ctx]
            [fern.protocols :as p]))

(defn pure
  "Given any value `v`, return it wrapped in
  the default/effect-free context.

  This is a multi-arity function that with arity `pure/1`
  uses the dynamic scope to resolve the current
  context. With `pure/2`, you can force a specific context
  value.

  Example:

      (with-context either/context
        (pure 1))
      ;; => #<Right [1]>

      (pure either/context 1)
      ;; => #<Right [1]>
  "
  ([v] (pure (ctx/infer) v))
  ([ctx v] (p/-pure ctx v)))

(defn return
  "This is a monad version of `pure` and works
  identically to it."
  ([v] (return (ctx/infer) v))
  ([ctx v] (p/-mreturn ctx v)))

(defn bind
  "Given a monadic value `mv` and a function `f`,
  apply `f` to the unwrapped value of `mv`.

      (bind (either/right 1) (fn [v]
                               (return (inc v))))
      ;; => #<Right [2]>

  For convenience, you may prefer to use the `mlet` macro,
  which provides a beautiful, `let`-like syntax for
  composing operations with the `bind` function."
  [mv f]
  (let [ctx (ctx/infer mv)]
    (p/-mbind ctx mv (fn [v]
                       (ctx/with-context ctx
                         (f v))))))

(defn extract
  "Generic function to unwrap/extract
  the inner value of a container."
  [v]
  (p/-extract v))

(defn join
  "Remove one level of monadic structure.
  This is the same as `(bind mv identity)`."
  [mv]
  (bind mv identity))

(defn fmap
  "Apply a function `f` to the value wrapped in functor `fv`,
  preserving the context type."
  ([f]
   (fn [fv]
     (fmap f fv)))
  ([f fv]
   (let [ctx (ctx/infer fv)]
     (ctx/with-context ctx
       (p/-fmap ctx f fv)))))

(defn fapply
  "Given a function wrapped in a monadic context `af`,
  and a value wrapped in a monadic context `av`,
  apply the unwrapped function to the unwrapped value
  and return the result, wrapped in the same context as `av`.

  This function is variadic, so it can be used like
  a Haskell-style left-associative fapply."
  [af & avs]
  {:pre [(seq avs)]}
  (let [ctx (ctx/infer af)]
    (reduce (partial p/-fapply ctx) af avs)))

(defmacro when
  "Given an expression and a monadic value,
  if the expression is logical true, return the monadic value.
  Otherwise, return nil in a monadic context."
  ([b mv]
   `(if ~b
      (do ~mv)
      (fern.cats/pure nil)))
  ([ctx b mv]
   `(if ~b
      (do ~mv)
      (fern.cats/pure ~ctx nil))))

(defmacro unless
  "Given an expression and a monadic value,
  if the expression is not logical true, return the monadic value.
  Otherwise, return nil in a monadic context."
  ([b mv]
   `(if (not ~b)
      (do ~mv)
      (cats.core/pure nil)))
  ([ctx b mv]
   `(if (not ~b)
      (do ~mv)
      (cats.core/pure ~ctx nil))))

(defmacro mlet
  "Monad composition macro that works like Clojure's `let`. This
  facilitates much easier composition of monadic computations.

  Let's see an example to understand how it works.  This code uses
  bind to compose a few operations:

         (bind (just 1)
               (fn [a]
                 (bind (just (inc a))
                         (fn [b]
                           (return (* b 2))))))
         ;=> #<Just [4]>

  Now see how this code can be made clearer by using the mlet macro:

         (mlet [a (just 1)
                b (just (inc a))]
           (return (* b 2)))
         ;=> #<Just [4]>
     "
  [bindings & body]
  (when-not (and (vector? bindings)
                 (not-empty bindings)
                 (even? (count bindings)))
    (throw (IllegalArgumentException. "bindings has to be a vector with even number of elements.")))
  (->> (reverse (partition 2 bindings))
       (reduce (fn [acc [l r]]
                 (case l
                   :let  `(let ~r ~acc)
                   :when `(bind (guard ~r)
                                (fn [~(gensym)] ~acc))
                   `(bind ~r (fn [~l] ~acc))))
               `(do ~@body))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Applicative Let Macro
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- deps
  [expr syms]
  (cond
    (and (symbol? expr)
         (contains? syms expr))
    (list expr)

    (seq? expr)
    (mapcat #(deps % syms) expr)

    :else
    '()))

(defn- rename-sym
  [expr renames]
  (get renames expr expr))

(defn- rename
  [expr renames]
  (cond
    (symbol? expr)
    (rename-sym expr renames)
    (seq? expr)
    (map #(rename % renames) expr)
    :else
    expr))

(defn- dedupe-symbols*
  [sym->ap body]
  (letfn [(renamer [{:keys [body syms aps seen renames] :as summ} [s ap]]
           (let [ap' (rename ap renames)
                 new-aps (conj aps ap')]
             (if (seen s)
               (let [s' (gensym)
                     new-syms (conj syms s')
                     new-seen (conj seen s')
                     new-renames (assoc renames s s')
                     new-body (rename body new-renames)]
                 {:syms new-syms
                  :aps new-aps
                  :seen new-seen
                  :renames new-renames
                  :body new-body})
               (let [new-syms (conj syms s)
                     new-seen (conj seen s)]
                 {:syms new-syms
                  :aps new-aps
                  :seen new-seen
                  :renames renames
                  :body body}))))]
    (let [summ
          (reduce renamer
                  {:syms []
                   :aps []
                   :seen #{}
                   :renames {}
                   :body body}
                  sym->ap)]
      [(mapv vector (:syms summ) (:aps summ)) (:body summ)])))

(defn- dedupe-symbols
  [bindings body]
  (let [syms (map first bindings)
        aps (map second bindings)
        sym->ap (mapv vector syms aps)]
    (dedupe-symbols* sym->ap body)))

(defn- dependency-map
  [sym->ap]
  (let [syms (map first sym->ap)
        symset (set syms)]
    (into []
          (for [[s ap] sym->ap
                :let [ds (set (deps ap symset))]]
            [s ds]))))

(defn- remove-deps
  [deps symset]
  (let [removed (for [[s depset] deps]
                  [s (clojure.set/difference depset symset)])]
    (into (empty deps) removed)))

(defn- topo-sort*
  [deps seen batches current]
  (if (empty? deps)
    (conj batches current)
    (let [dep (first deps)
          [s dependencies] dep
          dependant? (some dependencies seen)]
      (if (nil? dependant?)
        (recur (subvec deps 1)
               (conj seen s)
               batches
               (conj current s))
        (recur (remove-deps (subvec deps 1) (set current))
               (conj seen s)
               (conj batches current)
               [s])))))

(defn- topo-sort
  [deps]
  (let [syms (into #{} (map first deps))]
    (topo-sort* deps #{} [] [])))

(defn- bindings->batches
  [bindings]
  (let [syms (map first bindings)
        aps (map second bindings)
        sym->ap (mapv vector syms aps)
        sorted-deps (topo-sort (dependency-map sym->ap))]
    sorted-deps))

(defn- alet*
  [batches env body]
  (let [fb (first batches)
        rb (rest batches)
        fs (first fb)
        fa (get env fs)
        code
        (reduce (fn [acc syms]
                  (let [fs (first syms)
                        fa (get env fs)
                        rs (rest syms)
                        faps (map #(get env %) rs)]
                    (if (= (count syms) 1)
                      `(fmap (fn [~fs] ~acc) ~fa)
                      (let [cf (reduce (fn [f sym] `(fn [~sym] ~f))
                                       acc
                                       (reverse syms))]
                        `(fapply (fmap ~cf ~fa) ~@faps)))))
                `(do ~@body)
                (reverse batches))
        join-count (dec (count batches))]
    (reduce (fn [acc _]
            `(join ~acc))
        code
        (range join-count))))

(defmacro alet
  "Applicative composition macro similar to Clojure's `let`. This
  macro facilitates composition of applicative computations using
  `fmap` and `fapply` and evaluating applicative values in parallel.

  Let's see an example to understand how it works.  This code uses
  fmap for executing computations inside an applicative context:

       (fmap (fn [a] (inc a)) (just 1))
       ;=> #<Just [2]>

  Now see how this code can be made clearer by using the alet macro:

       (alet [a (just 1)]
         (inc a))
       ;=> #<Just [2]>

  Let's look at a more complex example, imagine we have dependencies
  between applicative values:

       (join
         (fapply
          (fmap
            (fn [a]
              (fn [b]
                (fmap (fn [c] (inc c))
                      (just (+ a b)))))
            (just 1))
          (just 2)))
       ;=> #<Just [4]>

  This is greatly simplified using `alet`:

       (alet [a (just 1)
              b (just 2)
              c (just (+ a b))]
         (inc c))
      ;=> #<Just [4]>

  The intent of the code is much clearer and evaluates `a` and `b` at
  the same time, then proceeds to evaluate `c` when all the values it
  depends on are available. This evaluation strategy is specially
  helpful for asynchronous applicatives."
  [bindings & body]
  (when-not (and (vector? bindings)
                 (not-empty bindings)
                 (even? (count bindings)))
    (throw (IllegalArgumentException. "bindings has to be a vector with even number of elements.")))
  (let [bindings (partition 2 bindings)
        [bindings body] (dedupe-symbols bindings body)
        batches (bindings->batches bindings)
        env (into {} bindings)]
    (if (and (= (count batches) 1)
             (= (count (map first bindings)) 1))
      `(fmap (fn [~@(map first bindings)]
               ~@body)
             ~@(map second bindings))
      (alet* batches env body))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; applicative "idiomatic apply"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro ap
  "Apply a pure function to applicative arguments, e.g.

   (ap + (just 1) (just 2) (just 3))
   ;; => #<Just [6]>
   (ap str [\"hi\" \"lo\"] [\"bye\" \"woah\" \"hey\"])
   ;; => [\"hibye\" \"hiwoah\" \"hihey\"
          \"lobye\" \"lowoah\" \"lohey\"]

   `ap` is essentially sugar for `(apply fapply (pure f) args)`,
   but for the common case where you have a pure, uncurried,
   possibly variadic function.

   `ap` actually desugars in `alet` form:

   (macroexpand-1 `(ap + (just 1) (just2)))
   ;; => (alet [a1 (just 1) a2 (just 2)] (+ a1 a2))

   That way, variadic functions Just Work, without needing to specify
   an arity separately.

   If you're familiar with Haskell, this is closest to writing
   \"in Applicative style\": you can straightforwardly convert
   pure function application to effectful application by with
   some light syntax (<$> and <*> in case of Haskell, and `ap` here).

   See the original Applicative paper for more inspiration:
   http://staff.city.ac.uk/~ross/papers/Applicative.pdf"
  [f & args]
  (let [syms (repeatedly (count args) (partial gensym "arg"))]
    `(alet [~@(interleave syms args)]
        (~f ~@syms))))

(defmacro ap->
  "Thread like `->`, within an applicative idiom.

  Compare:

  (macroexpand-1 `(-> a b c (d e f)))
  => (d (c (b a) e f)

  with:

  (macroexpand-1 `(ap-> a b c (d e f))
  => (ap d (ap c (ap b a) e f))
  "
  [x & forms]
  (loop [x x, forms forms]
    (if forms
      (let [form (first forms)
            threaded (if (seq? form)
                       (with-meta `(ap ~(first form) ~x ~@(next form)) (meta form))
                       `(ap ~form ~x))]
        (recur threaded (next forms)))
      x)))

(defmacro ap->>
  "Thread like `->>`, within an applicative idiom."
  [x & forms]
  (loop [x x, forms forms]
    (if forms
      (let [form (first forms)
            threaded (if (seq? form)
                       (with-meta `(ap ~(first form) ~@(next form)  ~x) (meta form))
                       `(ap ~form ~x))]
        (recur threaded (next forms)))
      x)))

(defmacro as-ap->
  "Thread like `as->`, within an applicative idiom."
  [expr name & forms]
  `(let [~name ~expr
         ~@(interleave (repeat name) (for [form forms] `(ap ~@form)))]
     ~name))
