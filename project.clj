(defproject sonian/carica "1.0.2"
  :description "A flexible configuration library"
  :url "https://github.com/sonian/carica"
  :dependencies [[cheshire "5.0.1"]
                 [org.clojure/tools.logging "0.2.4"]]
  :profiles {:dev
             {:resource-paths ["etc"],
              :dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.0-beta1"]]}}
  :aliases {"all" ["with-profile" "dev:1.5,dev"]}
  ;; For Lein 1
  :dev-dependencies [[org.clojure/clojure "1.4.0"]]
  :dev-resources-path "etc")
