(ns rate-gate.core
  (:import (java.util.concurrent Semaphore LinkedBlockingQueue TimeUnit)))

(defprotocol PRateGate
  (open? [this])
  (tarry [this] [this timeout-ms timeout-val])
  (shutdown [this]))

(deftype RateGate [n span-ms semaphore exit-times done thread]
  PRateGate
  (open? [_]
    (pos? (.availablePermits semaphore)))
  (tarry [_]
    (when-not @done
      (.acquire semaphore)
      (.offer exit-times (+ (System/nanoTime) (* span-ms 1000000)))))
  (tarry [_ timeout-ms timeout-val]
    (when-not @done
      (.tryAcquire semaphore timeout-ms TimeUnit/MILLISECONDS)
      (.offer exit-times (+ (System/nanoTime) (* span-ms 1000000)))))
  (shutdown [_]
    (reset! done true))
  (toString [this]
    (str "#<rate-gate: " n " per " span-ms " ms>"))
  clojure.lang.IDeref
  (deref [this]
    (tarry this))
  clojure.lang.IBlockingDeref
  (deref [this timeout-ms timeout-val]
    (tarry this timeout-ms timeout-val))
  clojure.lang.IFn
  (invoke [this]
    (shutdown this)))

(defmethod print-method RateGate [g w]
  (.write w (str g)))

(defn rate-gate
  "Creates a rate-gate object which can be used to limit the rate at which
  actions occur. Calling deref on the object will block until things are
  allowed to proceed (unless a timeout is also passed to deref). Spawns a
  management thread which can be shut down by calling the object with no
  arguments."
  [n span-ms]
  (let [semaphore (Semaphore. n true)
        exit-times (LinkedBlockingQueue.)
        done (atom false)
        thread (doto (Thread.
                      (fn []
                        (while (not @done)
                          (loop [exit-time (.peek exit-times)]
                            (when (and exit-time
                                       (>= 0 (- exit-time (System/nanoTime))))
                              (.release semaphore)
                              (.poll exit-times)
                              (recur (.peek exit-times))))
                          (let [delay (if-let [exit-time (.peek exit-times)]
                                        (/ (- exit-time (System/nanoTime)) 1000000)
                                        span-ms)]
                            (when (pos? delay)
                              (Thread/sleep delay))))))
                 (.setDaemon true)
                 (.start))]
    (RateGate. n span-ms semaphore exit-times done thread)))

(defn rate-limit
  "Rate-limits a given function with a rate-gate. The rate-gate object
  will be attached to the function's metadata in the :rate-gate slot."
  [f n span-ms]
  (let [gate (rate-gate n span-ms)]
    ^{:rate-gates (into [gate] (:rate-gates (meta f)))}
    (fn [& args]
      (tarry gate)
      (apply f args))))

(defn un-limit
  "Clears the rate-gate for a function previously passed to rate-limit"
  [f]
  (doseq [gate (:rate-gates (meta f))]
    (shutdown gate)))