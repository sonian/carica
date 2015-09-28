(ns carica.map)

(defn merge-nested
  "Recursively merge two Clojure trees of maps."
  [v1 v2]
  (if (and (map? v1) (map? v2))
    (merge-with merge-nested v1 v2)
    v2))
