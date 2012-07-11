# rate-gate

A simple Clojure mechanism for controlling the rate at which actions can occur. Particularly useful for enforcing rate limits on web requests because it enforces a hard (rather than average) limit, so you won't run afoul of an API's TOS.

Inspired by http://www.pennedobjects.com/2010/10/better-rate-limiting-with-dot-net/

## Usage

Leiningen coordinate:

```clj
[rate-gate "1.3.0"]
```

Basic usage:

```clj
(use '[rate-gate.core :only [rate-gate tarry shutdown rate-limit un-limit]])

;; Create a gate that allows two actions every second. Creating a gate
;; spawns a daemon thread to keep track of things.
(let [gate (rate-gate 2 1000)]
  (dotimes [_ 10]
    (tarry gate) ;blocks until ready
    (println "boop"))
  ;; Shuts down the management thread
  (shutdown gate))

;; f can be called at most once per second and 8 times per 10 seconds.
(def f (-> #(println "boop")
           (rate-limit 1 1000)
           (rate-limit 8 10000)))
(dotimes [_ 10] (time (f)))

;; Rate gates are attached to the function's metadata.
(:rate-gates (meta f))
;; => [#<rate-gate: 8 per 10000 ms> #<rate-gate: 1 per 1000 ms>]    

;; Rate limits can be removed from functions
(un-limit f)
(dotimes [_ 10] (time (f)))
```

## License

Copyright (C) 2011-2012 Justin Kramer

Distributed under the Eclipse Public License, the same as Clojure.
