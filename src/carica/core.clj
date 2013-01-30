(ns carica.core
  (:use [clojure.java.io :only [reader]])
  (:require [clojure.tools.logging :as log]
            [clojure.walk :as walk]))

(def json-enabled?
  (try
    (require 'cheshire.core)
    true
    (catch Throwable _
      false)))

(defn ^:dynamic json-parse-stream
  "Resolve and apply cheshire's json parsing dynamically."
  [& args]
  {:pre [json-enabled?]}
  (apply (ns-resolve (symbol "cheshire.core") (symbol "parse-stream")) args))

(defn resources
  "Search the classpath for resources matching the given path"
  [path]
  (when path
    (enumeration-seq
     (.getResources
      (.getContextClassLoader
       (Thread/currentThread))
      path))))

(defn merge-nested [v1 v2]
  (if (and (map? v1) (map? v2))
    (merge-with merge-nested v1 v2)
    v2))

(defmulti load-config (comp second
                            (partial re-find #"\.([^..]*?)$")
                            (memfn getPath)))

(defmethod load-config "clj" [resource]
  (try
    (eval
     (try
       (binding [*read-eval* false] (read-string (slurp resource)))
       (catch Throwable t
         (log/warn t "error reading config" resource)
         (throw
          (Exception. (str "error reading config " resource) t)))))
    (catch Throwable t
      (log/warn t "error evaling config" resource)
      (throw
       (Exception. (str "error evaling config " resource) t)))))

(defmethod load-config "json" [resource]
  (with-open [s (.openStream resource)]
    (-> s reader (json-parse-stream true))))

(defn get-configs
  "Takes a data structure of config resources (URLs) in priority order and
  merges them together.  The resources can be a simple list where first-in wins.
  Additionally the structure may contain maps where the key becomes the
  effective namespace of the resources in the value.

  Each node is handled by type:
  - resources (URL): load the config
  - collections (except for maps): merge the members
  - all others, return as is

  E.g., the following:
  [#<URL file:/some/path1>
   {:ns1 [#<URL file:/some/path2> #<URL file:/some/path3>]}]

  would become:
  {<keys and values from /some/path>
   :ns1 {<the merged keys and value from path2 and path3>}}"
  [resources]
  (walk/postwalk
   (fn [n]
     (cond (= java.net.URL (class n))
           (load-config n)
           (map? n)
           n
           ;; don't include vectorized maps
           (and (coll? n) (coll? (first n)))
           (apply merge-with merge-nested (reverse n))
           (nil? n)
           {}
           :else
           n))
   resources))

(defn config*
  "Looks up the keys in the maps.  If not found, log and return nil."
  [m ks]
  (let [v (get-in m ks ::not-found)]
    (if (= v ::not-found)
      (log/warn ks "isn't a valid config")
      v)))

(defn configurer
  "Given a the list of resources in the format expected by get-configs,
  return a function that can be used to search the configuration files
  in the following manner"
  [resources]
  (let [configs (get-configs resources)]
    (fn [& ks]
      (config* configs ks))))

(def ^:dynamic config
  "The default config function. It searches for carica.clj and carica.json
  on the classpath (with json taking preference) and returns a fuction with
  the signature of (fn [& ks] ...)

  To retrieve a config value in the following configuration...

  {:name \"bob\"
   :address {:street \"42 Main St.\" :city \"...\" ...}}

  ...one would call (config :address :street) to retrieve \"42 Main St.\""
  (configurer (concat (resources "config.json")
                      (resources "config.clj"))))

(defn reduce-into-map [overrides]
  (let [[val & keys] (reverse overrides)]
    (reduce (fn [v k] (hash-map k v)) val keys)))

(defn overrider* [cfg-fn-var]
  (fn [& overrides]
    (let [c (merge-nested (cfg-fn-var) (reduce-into-map overrides))]
      (fn [& ks]
        (config* c ks)))))

(defmacro overrider [cfg-fn]
  `(overrider* (var ~cfg-fn)))

(def override-config
  "Useful for testing, override-config enables overriding config
  values. It takes a series of keys and a replacement value.

  E.g., these are all equivalent:
  (with-redefs [config (override-config {:address {:street \"42 Broadway\"}})
  (with-redefs [config (override-config :address {:street \"42 Broadway\"})
  (with-redefs [config (override-config :address :street \"42 Broadway\")

  It isn't possible to remove any values, though they can be replaced with nil.
  E.g.,
  (with-redefs [config (override-config nil)])"
  (overrider config))
