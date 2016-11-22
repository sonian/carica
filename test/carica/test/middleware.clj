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

(deftest test-env-override-config
  (let [env (atom "prod")]
    (with-redefs [write! (fn [& _]) ;; quiet the warnings
                  getenv (fn [_] @env)]
      (testing "config overriding works"
        (let [env-config (configurer
                          (resources "config.clj")
                          [(env-override-config "NOOP" :env-config)])]
          (is (= "please" (env-config :magic-word)))
          (is (= "sugar on top" (env-config :extra)))
          (is (= 2 (env-config :test :nested :map)))))
      (testing "a different env"
        (reset! env "dev")
        (let [env-config (configurer
                          (resources "config.clj")
                          [(env-override-config "NOOP" :env-config)])]
          (is (= "abrakadabra" (env-config :magic-word)))
          (is (nil? (env-config :extra)))))
      (testing "nonexistant env doesn't meddle"
        (reset! env "test")
        (let [env-config (configurer
                          (resources "config.clj")
                          [(env-override-config "NOOP" :env-config)])]
          (is (= "mellon" (env-config :magic-word)))
          (is (nil? (env-config :extra)))))
      (testing "parent key can be nil"
        (reset! env "prod")
        (let [env-config (configurer
                          (resources "config.clj")
                          [(env-override-config "NOOP")])]
          (is (= "hocus pocus" (env-config :magic-word)))
          (is (nil? (env-config :extra))))))))

(deftest test-env-substitute-config
  (testing "the envvar value is in the location"
    (with-redefs [getenv (constantly "Now.")]
      (let [env-config (configurer
                        (resources "config.clj")
                        [(env-substitute-config "NOOP" :magic-word)
                         (env-substitute-config "NOOP"
                                                :i-totally :dont-exist)])]
        (is (= "Now." (env-config :magic-word))
            "Should see our overridden value.")
        (is (= "Now." (env-config :i-totally :dont-exist))
            "Nested key paths should work, even if they aren't defined."))))
  (testing "a missing envvar returns the configured default"
    (with-redefs [getenv (constantly nil)]
      (let [env-config (configurer
                        (resources "config.clj")
                        [(env-substitute-config "NOOP" :magic-word)])]
        (is (= "mellon" (env-config :magic-word))
            "Should see our configured default value.")))))
