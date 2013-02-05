(ns carica.test.core
  (:require [carica.core :refer :all]
            [clojure.test :refer :all]
            [clojure.tools.logging.impl :refer [write!]]))

(deftest config-test
  (testing "config"
    (testing "should offer a map of settings"
      (is (map? (config :nested-one-clj)))
      (testing "with the ability to get at nested values"
        (is (= "test-clj" (config :nested-one-clj :test-clj)))))
    (testing "should merge all maps on the classpath"
      (is (= true (config :from-test)))
      (is (= true (config :from-etc)))
      (testing "but the first on the classpath should win"
        (is (= "test" (config :merged-val)))))
    (testing "should be overridden with override-config"
      (with-redefs [config (override-config
                            {:nested-multi-json {:test-json {:test-json 21}
                                                 :hello :world}})]
        (is (= :world (config :nested-multi-json :hello)))
        (is (= 21 (config :nested-multi-json :test-json :test-json)))))
    (testing "even if it's all made up"
      (with-redefs [config (override-config :common :apply :hash-map)]
        (is (= :hash-map (config :common :apply)))))
    (testing "should return nil and warn if a key isn't found"
      (let [called (atom false)]
        (with-redefs [write! (fn [& _] (do (reset! called true) nil))]
          (is (nil? (config :test-multi-clj :non-existent-key)))
          (is @called))))
    (testing "should return nil and not warn if a key has a nil value"
      (let [called (atom false)]
        (with-redefs [write! (fn [& _] (do (reset! called true) nil))]
          (is (nil? (config :nil-val)))
          (is (not @called)))))))

(deftest test-dynamic-config
  (testing "config should be dynamic, for runtime repl overrides"
    (is (.isDynamic #'config)))
  (testing "also, it should work when rebound"
    ;; this is the same test as 'should be overridden with
    ;; override-config' above, only rewritten to use binding
    (binding [config (override-config
                      {:nested-multi-json {:test-json {:test-json 21}
                                           :hello :world}})]
      (is (= :world (config :nested-multi-json :hello)))
      (is (= 21 (config :nested-multi-json :test-json :test-json))))
    (binding [config (override-config :common :apply :hash-map)]
      (is (= :hash-map (config :common :apply))))))

(deftest test-json-config
  (is (= 42 (config :json-only :nested))))

(deftest test-jar-json-config-loading
  ;; make sure we get the missing jar exception, not the exception
  ;; that comes from calling `file` on a jar: URL
  (is (thrown? java.io.IOException
               (load-config (java.net.URL. "jar:file:///foo.jar!/bar.json")))))

(deftest nested-config-redefs-are-okay
  (with-redefs [config (override-config :foo {:baz 42 :quux :x})]
    (with-redefs [config (override-config :foo {:quux :y})]
      (is (= (config :foo) {:baz 42 :quux :y})))))

(deftest nested-missing-keys-are-acceptable
  (with-redefs [write! (fn [& _] nil)]
    (is (not (config :nested-multi-clj :missing :missing :missing :missing)))))

(deftest nil-resources-are-handled
  (is (= (get-configs [(resources "config.clj")])
         (get-configs [nil (resources "config.clj") nil [nil nil]]))))

(deftest test-middleware
  (let [call-count (atom 0)
        call-mdlware (fn [f]
                       (fn [cfg-map]
                         (swap! call-count inc)
                         (f cfg-map)))
        empty-cfg (configurer (resources "config.clj"))
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
