(ns carica.test.middleware
  (:require [carica.core :refer [configurer resources config]]
            [carica.middleware :refer :all]
            [clojure.test :refer :all]
            [clojure.tools.logging.impl :refer [write!]]))

(deftest test-middleware
  (let [call-count (atom 0)
        call-mdlware (fn [f]
                       (fn [resources]
                         (swap! call-count inc)
                         (f resources)))
        empty-cfg (configurer (resources "config.clj") [])
        mdlware-cfg (configurer (resources "config.clj")
                                [call-mdlware])
        cached-cfg (configurer (resources "config.clj")
                               [call-mdlware cache-config])
        eval-cfg (configurer (resources "config.clj")
                             [eval-config])]
    (testing "General middleware"
      (is (= true (empty-cfg :from-test)))
      (is (= 0 @call-count))
      (is (= true (mdlware-cfg :from-test)))
      (is (= 1 @call-count)))
    (testing "Caching works"
      (is (= true (cached-cfg :from-test)))
      (is (= true (cached-cfg :from-test)))
      (is (= 2 @call-count)))
    (testing "Eval works"
      (is (= '(+ 1 1) (empty-cfg :eval-cfg)))
      (is (= 2 (eval-cfg :eval-cfg))))))

(deftest test-wrap-middleware
  (let [call-count (atom 0)
        call-mdlware (fn [f]
                       (fn [resources]
                         (swap! call-count inc)
                         (f resources)))
        cached-cfg (configurer (resources "config.clj")
                               [call-mdlware cache-config])
        eval-cfg (configurer (resources "config.clj")
                             [eval-config])]
    (testing "Resetting cache works"
      (is (= true (cached-cfg :from-test)))
      (is (= true (cached-cfg :from-test)))
      (is (= 1 @call-count))
      (clear-config-cache! cached-cfg)
      (is (= true (cached-cfg :from-test)))
      (is (= 2 @call-count)))))
