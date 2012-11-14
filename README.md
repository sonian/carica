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

Carica looks for the config files on the classpath.

In your project.clj, add a directory to your resources-path, for these
examples, I'll be using "etc":

```clojure
:resources-path "etc"
```

Now, create an "etc" directory at the root of your project. Create
and open "etc/config.clj" in your favorite editor. 

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

```clojure
(ns my-proj.config
  (:require [carica.core :refer [configurer
                                 resources]]))

(def config (configurer (resources "proj_config.clj")))
```

Calling `my-proj.config/config` will work the same as calling
`carica.core/config` except that it will use your config file.

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

## License

Copyright (C) 2012 Sonian, Inc.

Distributed under the Eclipse Public License, the same as Clojure.
