(ns fern.protocols)

;; ========================================
;; There are some common ways to describe the functions we need.

(defprotocol Functor
  (-fmap [m f s]))

(defprotocol Applicative
  (-pure   [m v])
  (-fapply [m af av]))

(defprotocol Comonad
  (-extract [p]))

(defprotocol Monad
  (-mbind [m mv f])
  (-mreturn [m v]))

(defprotocol Printable
  (-repr [this]))

;; ========================================
;; It's often possible to infer what context the protocol functions
;; should be called under. (In a strongly-typed language that context
;; would come from the explicit or inferred types. In a dynamic
;; language, it comes from values.)

(defprotocol Context
  "Marker protocol for identifying the valid context types.")

(defprotocol Contextual
  (-get-context [_] "get the context associated with the type."))
