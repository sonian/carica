(ns carica.core
  (:use [clojure.java.io :only [reader input-stream] :as io])
  (:require [clojure.tools.logging :as log]
            [clojure.tools.reader :as clj-reader]
            [clojure.tools.reader.edn :as edn]
            [clojure.tools.reader.reader-types :as readers]
            [clojure.walk :as walk]))

(declare config)

(def json-enabled?
  "Determine if cheshire is loaded and json parsing is available."
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

(defn merge-nested
  "Recursively merge two Clojure trees of maps."
  [v1 v2]
  (if (and (map? v1) (map? v2))
    (merge-with merge-nested v1 v2)
    v2))

(defn load-with [resource loader]
  (try
    (-> resource input-stream readers/input-stream-push-back-reader loader)
    (catch Throwable t
      (log/warn t "error reading config" resource)
      (throw
       (Exception. (str "error reading config " resource) t)))))

(defmulti load-config
  "Load and read the config into a map of Clojure maps.  Dispatches
  based on the file extension."
  (fn [resource]
    (->> (if (string? resource)
           resource
           (.getPath resource))
         (re-find #"\.([^..]*?)$")
         second
         (keyword "carica"))))

(defmethod load-config :carica/edn [resource]
  (load-with resource edn/read))

(defmethod load-config :carica/clj [resource]
  (load-with resource clj-reader/read))

(defmethod load-config :carica/json [resource]
  (with-open [s (.openStream resource)]
    (-> s reader (json-parse-stream true))))

(derive :carica/clj :carica/edn)

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
     (cond (map? n)
           n
           ;; don't include vectorized maps
           (and (coll? n) (coll? (first n)))
           (apply merge-with merge-nested (reverse n))
           (nil? n)
           {}
           (satisfies? io/Coercions n)
           (load-config n)
           :else
           n))
   resources))

(defn get-config-fn
  "Retrieve the wrapped fn from the middleware, or return f if
  it isn't wrapped."
  [f]
  {:post [f]}
  (if (map? f)
    (:carica/fn f)
    f))

(defn get-options
  "Retrieve the exposed options from the wrapped middleware, or return
  {} if the middleware isn't wrapped."
  [f]
  {:post [f]}
  (if (map? f)
    (:carica/options f)
    {}))

(defn unwrap-middleware-fn
  "Convenience function for pulling the fn and options out of the
  wrapped middleware.  It's easier to destructure a seq than a map
  with namespaced keys."
  [f]
  [(get-config-fn f) (get-options f)])

(defn wrap-middleware-fn
  "Take the passed function and an optional map of options and return
  the wrapped middleware map that the rest of the code expects to
  use."
  [f & [opt-map]]
  {:carica/options (or opt-map {})
   :carica/fn f})

(defn eval-config
  "Config middleware that will evaluate the config map.  This allows
  arbitrary code to live in the config file.  It is often useful for
  coercing config values to a particular type."
  [f]
  (fn [resources]
    (let [cfg-map (f resources)]
      (try
        (eval cfg-map)
        (catch Throwable t
          (log/warn t "error evaling config" cfg-map)
          (throw
           (Exception. (str "error evaling config " cfg-map) t)))))))

(defn cache-config
  "Config middleware that will cache the config map so that it is
  loaded only once."
  [f]
  (let [mem (atom {})]
    (wrap-middleware-fn
     (fn [resources]
       (if-let [e (find @mem resources)]
         (val e)
         (let [ret (f resources)]
           (swap! mem assoc resources ret)
           ret)))
     {:carica/mem mem})))

(defn clear-config-cache!
  "Clear the cached config.  If a custom config function has been
  defined, it must be passed in."
  [& [config-fn]]
  (when ((or config-fn config) :carica/middleware :carica/mem)
    (swap! ((or config-fn config) :carica/middleware :carica/mem) empty)))

(defn config*
  "Looks up the keys in the maps.  If not found, log and return nil."
  [m ks]
  (let [v (get-in m ks ::not-found)]
    (if (= v ::not-found)
      (log/warn ks "isn't a valid config")
      v)))

(def default-middleware
  "The default list of middleware carica uses."
  [eval-config
   cache-config])

(defn middleware-compose
  "Unwrap and rewrap the function and middleware.  Used in a reduce to
  create the actual config funtion that is eventually called to fetch
  the values from the config map."
  [f mw]
  (let [[f-fn f-opts] (unwrap-middleware-fn f)
        [mw-fn mw-opts] (unwrap-middleware-fn mw)
        ;; mw-fn might return wrapped mw, so pull *it* apart as well
        [new-f-fn new-f-opts] (unwrap-middleware-fn (mw-fn f-fn))]
    (wrap-middleware-fn new-f-fn
                        (merge f-opts mw-opts new-f-opts))))

(defn configurer
  "Given a the list of resources in the format expected by get-configs,
  return a function that can be used to search the configuration files
  in the following manner.

  Additionally, configurer can take a seq of config middleware.  Each
  middleware function is called with a single function as an input and
  should return a function that takes the config map as an input.  See
  cache-config or eval-config for example middleware.  Middleware is
  applied in the order in which it is defined in the map.  If you do
  not provide any middleware, then the default middlware will be used."
  ([resources]
     (configurer resources default-middleware))
  ([resources middleware]
     (let [[config-fn options]
           (unwrap-middleware-fn
            (reduce middleware-compose
                    (wrap-middleware-fn get-configs)
                    middleware))]
       (fn [& ks]
         (if (= (first ks) :carica/middleware)
           (get-in options (rest ks))
           (config* (config-fn resources)
                    ks))))))

(def ^:dynamic config
  "The default config function.  It searches for config.json,
   config.edn and config.clj on the classpath (in that order)
   and returns a fuction with the signature of (fn [& ks] ...)

  To retrieve a config value in the following configuration...

  {:name \"bob\"
   :address {:street \"42 Main St.\" :city \"...\" ...}}

  ...one would call (config :address :street) to retrieve \"42 Main St.\""
  (configurer (concat (resources "config.json")
                      (resources "config.edn")
                      (resources "config.clj"))))

(defn reduce-into-map
  "Turns the flat list of keys -> value into a tree of maps.
  E.g., [:foo :bar :baz 4] becomes {:foo {:bar {:baz 4}}}"
  [overrides]
  (let [[val & keys] (reverse overrides)]
    (reduce (fn [v k] (hash-map k v)) val keys)))

(defn overrider*
  "Creates a custom overrider function from the given config function
  var."
  [cfg-fn-var]
  (fn [& overrides]
    (let [c (merge-nested (cfg-fn-var) (reduce-into-map overrides))]
      (fn [& ks]
        (config* c ks)))))

(defmacro overrider
  "Convenience macro to get the var for the passed config function and
  create the overrider function."
  [cfg-fn]
  `(overrider* (var ~cfg-fn)))

(def override-config
  "Useful for testing, override-config enables overriding config
  values.  It takes a series of keys and a replacement value.

  E.g., these are all equivalent:
  (with-redefs [config (override-config {:address {:street \"42 Broadway\"}})
  (with-redefs [config (override-config :address {:street \"42 Broadway\"})
  (with-redefs [config (override-config :address :street \"42 Broadway\")

  It isn't possible to remove any values, though they can be replaced with nil.
  E.g.,
  (with-redefs [config (override-config nil)])"
  (overrider config))
