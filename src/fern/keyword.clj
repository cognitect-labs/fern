(ns fern.keyword)

(defn renamespace
  "Move a key from one namespace to another. `nm` may be a symbol or
  nil. If nil, the new keyword has no namespace part."
  ([k]
   (renamespace k nil))
  ([k nm]
   {:pre [(keyword? k) (or (symbol? nm) (nil? nm))]}
   (if nm
     (keyword (str nm) (name k))
     (keyword (name k)))))
