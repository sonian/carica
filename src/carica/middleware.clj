(ns carica.middleware
  (:require [carica.map :refer [merge-nested]]
            [clojure.tools.logging :as log]))

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
  (let [config-fn (or config-fn (ns-resolve 'carica.core 'config))]
    (when (config-fn :carica/middleware :carica/mem)
      (swap! (config-fn :carica/middleware :carica/mem) empty))))

(defn getenv
  "This indirection allows overriding in tests"
  [env-var-name]
  (System/getenv env-var-name))

(defn env-override-config
  "Middleware that will override keys based on the value of a
  configurable environment variable and optional parent key.

  The environment variable defaults to CARICA_ENV with no parent
  key in the map.

  E.g., given the following config map:

  {:some :other
   :values :here
   :retries 1
   :env-override
   {:prod {:some :awesome :retries 5}}}

  Then adding the following to the list of Carica middleware
  will result in the config shown below.

  (env-override-config \"MY_CFG_VAR\" :env-override)

  {:some :awesome
   :values :here
   :retries 5}"
  ([]
   (env-override-config "CARICA_ENV"))
  ([env-var-name]
   (env-override-config env-var-name nil))
  ([env-var-name parent-key]
   (let [env (keyword (getenv env-var-name))
         env-path (if parent-key
                    [parent-key env]
                    [env])]
     (fn [f]
       (fn [resources]
         (let [cfg-map (f resources)
               env-cfg (get-in cfg-map env-path)]
           (if (and env (map? env-cfg))
             (merge-nested cfg-map env-cfg)
             cfg-map)))))))

(defn env-substitute-config
  "Middleware that will override a known key with the value of an environment
  variable.

  Given this config map:
  {:something 1
   :otherthing 2}

  Given this middleware definition:
  (env-substitute-config \"MY_OTHER_THING\" :otherthing)

  Given an execution environment with a definition like this
  export MY_OTHER_THING=5

  This config will result:
  {:something 1
   :otherthing 5}"
  [env-var-name & keyseq]
  (let [env-val (getenv env-var-name)]
    (fn [f]
      (fn [resources]
        (let [cfg-map (f resources)]
          (if-not (nil? env-val)
            (assoc-in cfg-map keyseq env-val)
            cfg-map))))))
