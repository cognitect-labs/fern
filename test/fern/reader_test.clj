(ns fern.reader-test
  (:require  [clojure.test :refer :all]
             [fern.reader :as reader]))

(deftest test-read-empty
  (is (= nil (reader/read-string "")))
  (is (= {}  (reader/read-string "{}"))))

(deftest test-read-one
  (is (= {:foo "a string"}
         (reader/read-string "{foo \"a string\"}"))))
