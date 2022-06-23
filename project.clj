(defproject sonian/carica "1.2.4-SNAPSHOT"
  :description "A flexible configuration library"
  :url "https://github.com/sonian/carica"
  :dependencies [[cheshire "5.11.0"]
                 [org.clojure/tools.logging "1.2.4"]
                 [org.clojure/tools.reader "1.3.6"]]
  :profiles {:dev
             {:resource-paths ["etc"]
              :dependencies [[org.clojure/clojure "1.10.3"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}}
  :aliases {"all" ["with-profile" "dev:1.4,dev"]}
  ;; For Lein 1
  :dev-dependencies [[org.clojure/clojure "1.4.0"]]
  :dev-resources-path "etc")
