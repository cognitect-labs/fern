;; Copyright (c) 2014 - 2017 Cognitect, Inc.
;; Copyright 2013 Relevance, Inc.

;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;;     the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns dev
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
   [clojure.core.async :as async :refer [<! <!! alt!!]]
   [clojure.java.io :as io]
   [clojure.java.javadoc :refer [javadoc]]
   [clojure.pprint :refer [pprint]]
   [clojure.reflect :refer [reflect]]
   [clojure.repl :refer [apropos dir doc find-doc pst source]]
   [clojure.set :as set]
   [clojure.spec :as s]
   [clojure.string :as str]
   [clojure.test :as test]
   [clojure.tools.namespace.repl :refer [refresh refresh-all clear]]))

(defn run-tests []
  (binding [clojure.test/*test-out* *out*]
    (clojure.test/run-all-tests #"fern.+-test")))

(s/check-asserts true)
