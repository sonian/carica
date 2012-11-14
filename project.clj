(defproject sonian/carica "1.0.0"
  :description "A flexible configuration library"
  :dependencies [[cheshire "4.0.4"]
                 [org.clojure/tools.logging "0.2.3"]]
  :profiles {:dev
             {:resource-paths ["etc"],
              :dependencies [[org.clojure/clojure "1.4.0"]]}}
  ;; For Lein 1
  :dev-dependencies [[org.clojure/clojure "1.4.0"]]
  :dev-resources-path "etc")
