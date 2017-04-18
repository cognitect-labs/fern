(ns fern.either-test
  (:require [fern.either :refer :all]
            [clojure.test :refer :all]))

(deftest test-construction
  (testing "directly constructing left values"
    (are [actual] (left? actual)
      (left)
      (left nil)
      (left "bear left")))

  (testing "directly constructing right values"
    (are [actual] (right? actual)
      (right)
      (right nil)
      (right "right frog")))

  (testing "applicative bind with either"
    (are [actual] (right? actual)
      (pure either 100))))

(deftest test-extraction
  (are [v] (= v (extract (left v)))
    nil
    100
    "A string"
    {:a 'map})
  (are [v] (= v (extract (right v)))
    nil
    100
    "A String"
    {:a 'map}))

(deftest test-equality
  (testing "should be equal"
    (are [lhs rhs] (= lhs rhs)
      (left 100)  (left 100)
      (left nil)  (left nil)
      (right -1)  (right -1)
      (right nil) (right nil)
      (right "this is a string") (right "this is a string")))

  (testing "should not be equal"
    (are [lhs rhs] (not= lhs rhs)
      (left 100)  (right 100)
      (left nil)  (right nil)
      nil         (left nil)
      nil         (right nil)
      (right "a") (right "A"))))

(deftest test-function-application
  (testing "fmap applies function to rights"
    (are [expected actual] (= expected (extract actual))
      10 (fmap either inc (right 9))
      9  (fmap either inc (left 9)))))

(deftest test-pure
  (is (= (right {:a 1}) (pure either {:a 1}))))

(deftest test-branch
  (are [e left-fn right-fn expected] (= expected (extract (branch e left-fn right-fn)))
    (left 0)  inc dec  1
    (right 0) inc dec -1))

(deftest test-try-either
  (is (left?  (try-either (throw (ex-info "boom" {})))))
  (is (left?  (try-either (/ 1 0))))
  (is (right? (try-either {:a 1}))))
