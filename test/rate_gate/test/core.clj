(ns rate-gate.test.core
  (:use [rate-gate.core])
  (:use [clojure.test]))

(deftest no-thread-per-f-call
  (let [f (fn [] )
        fg (rate-limit f 100 100)]
    (doall (take 1000 (repeatedly fg)))
    (is (< (java.lang.Thread/activeCount) 50))))

(deftest no-thread-per-rate-limit
  (let [fs (doall (take 1000 (repeatedly (fn [] (rate-limit #() 1 10)))))]
    (is (< (java.lang.Thread/activeCount) 50))))
