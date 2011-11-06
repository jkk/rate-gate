# rate-gate

A simple Clojure mechanism for controlling the rate at which actions can occur. Particularly useful for enforcing rate limits on web requests because it enforces a hard (rather than average) limit, so you won't run afoul of an API's TOS.

Inspired by http://www.pennedobjects.com/2010/10/better-rate-limiting-with-dot-net/

## Usage

Leiningen coordinate:

    [rate-gate "1.0.0"]

Basic usage:

    (use '[rate-gate.core :only [rate-gate tarry close rate-limit timespan]])

    ;; Create a gate that allows two actions every second. Creating a gate
    ;; spawns a management thread to keep track of things.
    (let [gate (rate-gate 2 (timespan 1 :second))]
      (dotimes [_ 10]
        (tarry gate) ;blocks until ready
        (println "boop"))
      ;; Shuts down the management thread
      (close gate))

    ;; f can be called at most once per second and 8 times per 10 seconds.
    ;; Note that with rate-limit, management threads are never shut down.
    (def f (-> #(println "boop")
               (rate-limit 1 (timespan 1 :second))
               (rate-limit 8 (timespan 10 :seconds))))
    (dotimes [_ 10] (time (f)))

## License

Copyright (C) 2011 Justin Kramer

Distributed under the Eclipse Public License, the same as Clojure.
