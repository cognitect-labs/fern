;; Copyright (c) 2017 Cognitect, Inc.

;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;;     the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns user
  "Quick-start functions for interactive development. This file is
  automatically loaded by Clojure on startup.")

(defn dev
  "Loads and switches to the 'dev' namespace."
  []
  (require 'dev)
  (in-ns 'dev))
