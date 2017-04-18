(ns fern-test
  (:require [fern :as f]
            [clojure.test :refer :all])
  (:import [clojure.lang ExceptionInfo]))

(deftest test-missing-key
  (is (thrown? ExceptionInfo  (f/evaluate 'foo {})))
  (is (thrown? ExceptionInfo (f/evaluate 'foo '{foo bar}))))

(deftest test-evaluate-shallow
  (are [cfg expected] (= expected (f/lookup 'foo cfg))
    '{foo 5}            5
    '{foo [5]}          [5]
    '{foo [bar] bar 5} '[bar]))

(deftest test-evaluate-deep
  (are [cfg expected] (= expected (f/evaluate 'foo cfg))
    '{foo 5}             5
    '{foo [5]}           [5]
    '{foo [bar] bar 5}   [5]
    '{foo b b c c d d 5} 5))
