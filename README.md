# Carica

Carica is a flexible configuration library.

It offers:
* a simple lookup syntax
* support for both Clojure and JSON config files
* config file merging (if you have more than one config file)
  * Even if one is a Clojure file and the other is JSON
* code evaluation in Clojure files
* runtime override capabilities for testing
* easy default config file names (config.clj and config.json)
  * ability to override the defaults

## Setup

```clojure
[sonian/carica "1.0.4"]

;; *or*, when not using JSON config files:
[sonian/carica "1.0.4" :exclusions [[cheshire]]]

;; carica is compatible with clojure 1.4+
```

Carica looks for the config files on the classpath.

Leiningen will add a directory called "resources" to the classpath even 
though the directory is not created by default, so create a "resources" 
directory at the root of your project. Now, create and open "resources/config.clj" 
in your favorite editor.

```clojure
{:foobar-timeout 300 #_"In seconds"
 :favorite-hour-of-day 8 #_"0-23"
 :blacklist nil
 :export-dir "/mnt/export"
 :timeout-ms (* 20 60 1000) #_"20 minutes"
 :db {:classname "org.postgresql.Driver"
      :subprotocol "postgresql"
      :subname "//localhost/test"
      :username "cosmo"
      :password "toomanysecrets"}}
```

(If you're wondering about the #_"" comments, we've found that they're
less prone to errors in the configuration files. That is, from
accidental newlines or from pulling up the closing brace into a line
with a ;; comment.)

## Usage

Now, with all of that in place, open a new REPL session:

```clojure
(use '[carica.core])

(config :export-dir)
;;=> "/mnt/export"

(config :db :username)
;;=> "cosmo"

(config :blacklist)
;;=> nil

(config :non-existent-key)
;;=> nil (with a warning message logged)
```

That's it!

## Overriding the defaults

Maybe you already have a config file with a different name, or a
config.clj that you use for a different purpose. No problem. To
override what files Carica loads you can create your own `config`
function using the `configurer` function.

If you override the default `config` function in this manner you must
also override the `override-config` function if you intend to use it.

```clojure
(ns my-proj.config
  (:require [carica.core :refer [configurer
                                 resources]]))

(def config (configurer (resources "proj_config.clj")))
(def override-config (overrider config))
```

Calling `my-proj.config/config` will work the same as calling
`carica.core/config` except that it will use your config file.

## Middleware

You can change the resulting config map in Carica by adding
middleware.  For instance, if you trust the data you are reading and
want to have it evaluated, there is an `eval-config` middleware
provided.  If you want the config to be read only once from disk, you
can use the `cache-config` middleware.

```clojure
(ns my-proj.config
  (:require [carica.core :refer [configurer
                                 resources]]))

(def config (configurer (resources "proj_config.clj")
                        [eval-config
                         cache-config]))
```

In typical middleware fashion, eval-config and cache-config are
functions that take a function as their only argument and return a
function that takes a list of resources as its only input.  For
instance, an example cache-config function:

```clojure
(defn cache-config
  "Config middleware that will cache the config map so that it is
  loaded only once."
  [f]
  (memoize (fn [resources]
             (f resources))))
```

### Middleware options

Middleware can also expose some internal state and options to the
outside world by wrapping the function that is returned.  As an
example, the actual cache-config function:

```clojure
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
```

This ultimately returns:

```clojure
{:carica/options {:carica/mem <the-cache-atom>}
 :carica/fn <the-config-fn>}
```

This map is built up from all of the defined middleware so the keys
used in the ```cache-config``` function should be unique.  To access
the options, call ```config``` as normal with the beginning path of
```:carica/middleware```.

For example:

```clojure
(swap! (cached-cfg :carica/middleware :carica/mem) empty)
```

Note: Specifically in the the ```cache-config``` case there is a
```clear-config-cache!``` function that can be called.  See the doc
for that function if you use a custom defined ```config``` function.

## Testing

Sometimes, during tests, it's handy to be able to override config
values:

```clojure
(with-redefs [config (override-config :db :password "swordfish")]

  (config :db :password)
  ;;=> "swordfish"

  (config :db :username))
  ;;=> "cosmo"
```

Or: 

```clojure
(with-redefs [config (override-config :db {:username "wagstaff"
                                           :password "swordfish"})]
  (config :db :password)
  ;;=> "swordfish"

  (config :db :username))
  ;;=> "wagstaff"
```

Only the provided values will be overwritten.

## Mind the Classpath

Carica looks for resources on the classpath and merges them in order,
meaning that configuration files earlier in the classpath will take
precedence over those that come later.

When using Leiningen the classpath is built such that test comes
first, then src, followed by resources-path. Keep the order in mind
when dealing with multiple configuration files with Leiningen and when
building the classpath by hand.

## License

Copyright (C) 2013 Sonian, Inc.

Distributed under the Eclipse Public License, the same as Clojure.
