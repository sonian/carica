(defproject sonian/carica "1.0.3-SNAPSHOT"
  :description "A flexible configuration library"
  :url "https://github.com/sonian/carica"
  :dependencies [[cheshire "5.1.1"]
                 [org.clojure/tools.logging "0.2.4"]
                 [org.clojure/tools.reader "0.7.3"]]
  :profiles {:dev
             {:resource-paths ["etc"]
              :dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}}
  :aliases {"all" ["with-profile" "dev:1.4,dev"]}
  ;; For Lein 1
  :dev-dependencies [[org.clojure/clojure "1.4.0"]]
  :dev-resources-path "etc")
