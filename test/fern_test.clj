(ns fern-test
  (:require [clojure.test :refer :all]
            [clojure.tools.reader :as r]
            [clojure.tools.reader.reader-types :as rt]
            [fern :as f])
  (:import clojure.lang.ExceptionInfo))

(deftest test-missing-key
  (is (thrown? ExceptionInfo (f/evaluate (f/environment {}) 'foo)))
  (is (thrown? ExceptionInfo (f/evaluate (f/environment '{foo bar}) 'foo))))

(deftest test-evaluate-shallow
  (are [cfg expected] (= expected (get (f/environment cfg) 'foo))
    '{foo 5}            5
    '{foo [5]}          [5]
    '{foo [bar] bar 5} '[bar]))

(deftest test-evaluate-deep
  (are [cfg expected] (= expected (f/evaluate (f/environment cfg) 'foo))
    '{foo 5}             5
    '{foo [5]}           [5]
    '{foo [bar] bar 5}   [5]
    '{foo b b c c d d 5} 5))


(deftest test-fern-quote
  (are [cfg expected] (= expected (f/evaluate (f/environment cfg) 'foo))
    '{foo (fern/quote bar)}         'bar
    '{foo (fern/quote 'bar)}        '(quote bar)
    '{foo (fern/quote (quote bar))} '(quote bar)))

(def fern-with-lits
  "{fn :russ
    ln :olsen
    person (lit human fn ln)
   }")

(defmethod f/literal 'human
  [_ fn ln]
  (list fn ln))

(defn string->environment
  [s]
  (f/environment (r/read (rt/indexing-push-back-reader s))))

(deftest test-resolving-lit
  (let [cfg (string->environment fern-with-lits)]
    (is (= '(:russ :olsen) (f/evaluate cfg 'person)))))

(def diamond-reference
  "{A [B C]
    B D
    C D
    D (lit human \"Russ\" \"Olsen\")}")

(deftest test-identical-objects
  (let [cfg               (string->environment diamond-reference)
        [person2 person1] (f/evaluate cfg 'A)]
    (is (identical? person1 person2))))
