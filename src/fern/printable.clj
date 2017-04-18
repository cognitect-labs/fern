(ns fern.printable
  (:require [fern.protocols :as p]))

(defn make-printable
  [klass]
  (defmethod print-method klass
     [mv ^java.io.Writer writer]
     (.write writer (p/-repr mv))))
