(ns rate-gate.core
  (:require [clojure.core.async :as async])
  (:import (java.util.concurrent Semaphore LinkedBlockingQueue TimeUnit)))

(defmacro go-after
  "Asynchronously waits for n milliseconds, then executes body in another thread.  Returns nil."
  [n & body]
  `(do (async/go
        (async/alts! [(async/timeout ~n)])
        ~@body)
       nil))

(defprotocol PRateGate
  (open? [this])
  (tarry [this] [this timeout-ms timeout-val])
  (shutdown [this]))

(deftype RateGate [n span-ms semaphore done]
  PRateGate
  (open? [_]
    (pos? (.availablePermits semaphore)))
  (tarry [_]
    (when-not @done
      (.acquire semaphore)
      (go-after span-ms (.release semaphore))))
  (tarry [_ timeout-ms timeout-val]
    (when-not @done
      (if (.tryAcquire semaphore timeout-ms TimeUnit/MILLISECONDS)
        (go-after span-ms (.release semaphore)))))
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
  allowed to proceed (unless a timeout is also passed to deref)."
  [n span-ms]
  (let [semaphore (Semaphore. n true)
        done (atom false)]
    (RateGate. n span-ms semaphore done)))

(defn rate-limit
  "Rate-limits the given function with a rate-gate. The rate-gate object
  will be attached to the function's metadata in the :rate-gates slot."
  [f n span-ms]
  (let [gate (rate-gate n span-ms)]
    ^{:rate-gates (into [gate] (:rate-gates (meta f)))}
    (fn [& args]
      (tarry gate)
      (apply f args))))

(defn un-limit
  "Clears any rate-gates from a function previously passed to rate-limit"
  [f]
  (doseq [gate (:rate-gates (meta f))]
    (shutdown gate)))
