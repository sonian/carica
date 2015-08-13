{:from-test true

 :nil-val nil

 :merged-val "test"

 :test-clj "test-clj"
 :nested-one-clj {:test-clj "test-clj"}
 :nested-multi-clj {:test-clj {:test-clj "test-clj"}}

 :test-json "test-clj"
 :nested-one-json {:test-json "test-clj"}
 :nested-multi-json {:test-json {:test-json "test-clj"}}

 :quoted-vectors-work '[a b c]
 :read-eval-works #=(eval (+ 2 (* 10 4)))

 :magic-word "mellon"

 :prod {:magic-word "hocus pocus"}

 :env-config {:dev {:magic-word "abrakadabra"}
              :prod {:magic-word "please"
                     :extra "sugar on top"}}}
