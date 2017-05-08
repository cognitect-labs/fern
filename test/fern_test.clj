(ns fern-test
  (:require [clojure.test :refer :all]
            [clojure.tools.reader :as r]
            [clojure.tools.reader.reader-types :as rt]
            [fern.easy :as fe]
            [fern :as f])
  (:import clojure.lang.ExceptionInfo))

(deftest test-is-associative
  (testing "Environment is Associative"
    (is (= (empty (f/environment {:foo 33})) (f/environment {})))
    (is (= (f/environment {:foo 33}) (f/environment {:foo 33})))
    (is (= (assoc (f/environment {:foo 33}) :bar 99) (f/environment {:foo 33 :bar 99})))
    (is (= (get (f/environment {:foo 33}) :foo) 33))
    (is (= (:bar (f/environment {:foo 33}) 99) 99))
    (is (= (:foo (f/environment {:foo 33})) 33)))
  (testing "Environment is a Seqable"
    (is (= (seq (f/environment {:foo 33})) (seq {:foo 33}))))
  (testing "Environment is a Collection"
    (is (= (.entryAt (f/environment {:foo 33}) :foo) (.entryAt {:foo 33} :foo)))
    (is (= 2 (count (f/environment {:foo 1 :bar 2}))))))

(deftest test-missing-key
  (is (thrown? ExceptionInfo (f/evaluate (f/environment {}) 'foo)))
  (is (thrown? ExceptionInfo (f/evaluate (f/environment '{foo @bar}) 'foo))))

(deftest test-evaluate-shallow
  (are [cfg expected] (= expected (get (f/environment cfg) 'foo))
    '{foo 5}            5
    '{foo [5]}          [5]
    '{foo [bar] bar 5} '[bar]))

(deftest test-evaluate-deep
  (are [cfg expected] (= expected (f/evaluate (f/environment cfg) 'foo))
    '{foo 5}                   5
    '{foo [5]}                 [5]
    '{foo [@bar] bar 5}        [5]
    '{foo @b b @c c @d d 5}    5))


(deftest test-recursion-limit
  (is (thrown-with-msg? ExceptionInfo #"Runaway" (f/evaluate (f/environment '{foo @foo}) 'foo)))
  (is (thrown-with-msg?  ExceptionInfo #"Runaway"
                        (f/evaluate (f/environment '{foo @bar bar @baz baz @foo}) 'foo))))


(deftest test-fern-quote
  (are [cfg expected] (= expected (f/evaluate (f/environment cfg) 'foo))
    '{foo (quote bar)}                      'bar
    '{foo (quote 'bar)}                     '(quote bar)
    '{foo (quote (quote bar))}              '(quote bar)
    '{foo '(clojure.core/deref bar)}        '(clojure.core/deref bar)
    '{foo (quote (clojure.core/deref bar))} '(clojure.core/deref bar)))

(def sample (fe/file->environment "../fern/test/sample.fern"))

;; TBD Where is our file name???

(deftest test-metadata
  (testing "value for symbol"
    (are [sym md] (= md (meta (f/evaluate sample sym)))
         'fullname {:file "../fern/test/sample.fern"
                    :source "[@fn @ln]"
                    :line 3 :column 11 :end-line 3 :end-column 20}
         'revname  {:source "[\n    [@ln]\n    [@fn]\n  ]"
                    :file "../fern/test/sample.fern"
                    :line 5 :column 3 :end-line 8 :end-column 4}
         'nameref {:source "[\n    [@ln]\n    [@fn]\n  ]"
                   :file "../fern/test/sample.fern"
                   :line 5 :column 3 :end-line 8 :end-column 4}))

  (testing "components of value for symbol"
    (are [sym extr md] (= md (meta (get-in (f/evaluate sample sym) extr)))
         'revname  [0] {:source "[@ln]"
                        :file "../fern/test/sample.fern"
                        :line 6 :column 5 :end-line 6 :end-column 10})))

(defn string->environment
  [s]
  (f/environment (r/read (rt/indexing-push-back-reader s))))

(def fern-with-expression
  "{fn :russ
    ln :olsen
    person (str @fn @ln)}")

(deftest test-eval-function
  (let [cfg (string->environment fern-with-expression)]
    (is (= ":russ:olsen" (f/evaluate cfg 'person)))))

(defn boom []
  (/ 0 0))

(deftest test-missing-symbol-in-list
  (is (thrown-with-msg? ExceptionInfo #"Unable to resolve symbol"
                        (f/evaluate (string->environment "{foo (baz 1)}") 'foo)))
  (is (thrown-with-msg? ExceptionInfo #"foo.bar"
                        (f/evaluate (string->environment "{foo (foo.bar/baz 1)}") 'foo)))
  (is (thrown-with-msg? ExceptionInfo #"Divide by zero"
                        (f/evaluate (string->environment "{foo (fern-test/boom)}") 'foo))))

(def diamond-reference
  "{A [@B @C]
    B @D
    C @D
    D (list \"Russ\" \"Olsen\")}")

(deftest test-identical-objects
  (let [cfg               (string->environment diamond-reference)
        [person2 person1] (f/evaluate cfg 'A)]
    (is (identical? person1 person2))))

(deftest test-reflective-configurations
  (let [cfg (fe/load-environment "test/self-referential.fern")]
    (is (= 24 (f/evaluate cfg 'foo)))
    (is (= cfg (f/evaluate cfg 'baz)))))

(defrecord ARecord [fname lname])

(defprotocol AProtocol
  (the-val [this]))

(defn prot [v]
  (reify AProtocol
    (the-val [this]
      v)
    (equals [this that]
      (= v (the-val that)))))

(deftest test-protocol-and-record-realization
  (are [expected env] (= expected (f/evaluate (string->environment env) 'rec))
    #{:a :b :c}                "{rec #{@a @b @c} a :a b :b c :c}"
    (->ARecord "Russ" "Olsen") "{rec (fern-test/->ARecord \"Russ\" \"Olsen\")}"
    (->ARecord "Russ" "Olsen") "{rec @ref ref (fern-test/->ARecord \"Russ\" \"Olsen\")}"
    (->ARecord "Russ" "Olsen") "{rec @ref1 ref1 @ref2 ref2 (fern-test/->ARecord \"Russ\" \"Olsen\")}"
    (prot 5)                   "{rec (fern-test/prot 5)}"
    (prot 5)                   "{rec @ref ref (fern-test/prot 5)}"
    {:a (prot 5)}              "{rec @ref1 ref1 {:a (fern-test/prot @v1/ref2)} v1/ref2 5}"
    (prot {:a 5})              "{rec @ref1 ref1 (fern-test/prot {:a @v1/ref2}) v1/ref2 5}"))
