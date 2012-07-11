(ns rate-gate.core
  (:import (java.util.concurrent Semaphore LinkedBlockingQueue TimeUnit)))

(defn timespan [n unit]
  "Converts a timespan to milliseconds. E.g., (timespan 1 :second) => 1000"
  (* n (case unit
         :nanosecond   1/1000000
         :nanoseconds  1/1000000
         :microsecond  1/1000
         :microseconds 1/1000
         :millisecond  1
         :milliseconds 1
         :second       1000
         :seconds      1000
         :minute       60000
         :minutes      60000
         :hour         3600000
         :hours        3600000
         :day          86400000
         :days         86400000
         :week         604800000
         :weeks        604800000)))

(defn rate-gate
  "Creates a rate-gate object which can be used to limit the rate at which
  actions occur. Calling deref on the object will block until things are
  allowed to proceed (unless a timeout is also passed to deref). Spawns a
  management thread which can be shut down by calling the object with no
  arguments."
  [n span]
  (let [semaphore (Semaphore. n true)
        exit-times (LinkedBlockingQueue.)
        done (atom false)
        fut (future
              (while (not @done)
                (loop [exit-time (.peek exit-times)]
                  (when (and exit-time
                             (>= 0 (- exit-time (System/nanoTime))))
                    (.release semaphore)
                    (.poll exit-times)
                    (recur (.peek exit-times))))
                (let [delay (if-let [exit-time (.peek exit-times)]
                              (/ (- exit-time (System/nanoTime)) 1000000)
                              span)]
                  (Thread/sleep delay))))]
    (reify
      clojure.lang.IDeref
      (deref [_]
        (.acquire semaphore)
        (.offer exit-times (+ (System/nanoTime) (* span 1000000))))
      clojure.lang.IBlockingDeref
      (deref [_ timeout-ms timeout-val]
        (.tryAcquire semaphore timeout-ms TimeUnit/MILLISECONDS)
        (.offer exit-times (+ (System/nanoTime) (* span 1000000))))
      clojure.lang.IFn
      (invoke [_]
        (reset! done true)))))

(defn rate-limit
  "Rate-limits a given function with a rate-gate. Note that the rate gate
  is not returned, so there will be no way to shut down the management
  thread."
  [f n span]
  (let [gate (rate-gate n span)]
    (fn [& args]
      @gate
      (apply f args))))
