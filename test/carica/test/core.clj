(ns carica.test.core
  (:require [carica.core :refer :all]
            [clojure.test :refer :all]
            [clojure.tools.logging.impl :refer [write!]]
            [clojure.java.io :as io]))

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

(deftest test-edn-config
  (is (= "test-edn" (config :test-edn))))

(deftest test-config-embedded-in-jar
  (let [jar (-> "uberjared.jar" io/resource str)
        url (java.net.URL. (str "jar:" jar "!/config.edn"))
        cfg (configurer [url])]
    (is (= true (cfg :jar-resource)))))

(deftest test-configurer-with-other-io-types
  (let [config (configurer ["test/config.edn"])]
    (is (= "test-edn" (config :test-edn))))
  (let [config (configurer [(io/file "test/config.edn")])]
    (is (= "test-edn" (config :test-edn)))))

(deftest test-clj-vs-edn
  (with-redefs [write! (fn [& _] nil)]
    (let [edn-config (configurer (resources "edn-reader-cfg.edn") [])
          clj-config (configurer (resources "config.clj") [])]
      (is (= '(quote [a b c]) (clj-config :quoted-vectors-work)))
      (is (= 42 (clj-config :read-eval-works)))
      ;; quoted vectors in edn aren't read as one might expect.  They
      ;; end up as a map like {:foo } and throw an error.  Carica
      ;; catches the error and sends a warning to the log.
      (is (nil? (edn-config :quoted-vectors-fail))))))
