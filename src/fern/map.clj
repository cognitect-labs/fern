(ns fern.map)

;; ========================================
;; A couple of common utilities. These are careful to preserve
;; metadata.

(defn map-keys
  "Return a map with `f` applied to every key in `m`.
   Metadata is preserved."
  [f m]
  {:pre [(map? m) (fn? f)]}
  (with-meta (zipmap (map f (keys m)) (vals m)) (meta m)))

(defn map-vals
  "Return a map with `f` applied to every val in `m`.
   Metadata is preserved."
  [f m]
  {:pre [(map? m) (fn? f)]}
  (with-meta (zipmap (keys m) (map f (vals m))) (meta m)))
