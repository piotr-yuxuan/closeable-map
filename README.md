# `closeable-map`

[![](https://img.shields.io/clojars/v/piotr-yuxuan/closeable-map.svg)](https://clojars.org/piotr-yuxuan/closeable-map)
[![cljdoc badge](https://cljdoc.org/badge/piotr-yuxuan/closeable-map)](https://cljdoc.org/d/piotr-yuxuan/closeable-map/CURRENT)
[![GitHub license](https://img.shields.io/github/license/piotr-yuxuan/closeable-map)](https://github.com/piotr-yuxuan/closeable-map/blob/main/LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/piotr-yuxuan/closeable-map)](https://github.com/piotr-yuxuan/closeable-map/issues)

This small library defines a new type of Clojure map that represents
an execution context, and that you can close. When passed to
`with-open`, its closeable values will be closed. You can also use an
optional key `:close` to define your own close function, or a
collection of such functions.

It is a tiny alternative to more capable projects:
- Application state management:
  [stuartsierra/component](https://github.com/stuartsierra/component),
  [weavejester/integrant](weavejester/integrant),
  [tolitius/mount](https://github.com/tolitius/mount), _et al_.
- Extension of `with-open`:
  [jarohen/with-open](https://github.com/jarohen/with-open)
- Representing state in a map:
  [robertluo/fun-map](https://github.com/robertluo/fun-map)

## TL;DR example

``` clojure
;; in your project
(require '[piotr-yuxuan/closeable-map :refer [closeable-map]])

(defn start
  "Return a running context with values that can be closed."
  [config]
  (closeable-map {:server (http/start-server (api context) {:port 3030})
                  :producer (kafka-producer config)
                  :consumer (kafka-consumer config)}))

;; in test file
(with-open [context (start config)]
  (testing "unit test with isolated, repeatable context"
    (is (= :yay/🚀 (some-business/function context)))))
```

Syntactic sugar:

``` clojure
(require '[piotr-yuxuan/closeable-map :refer [closeable-hash-map]])

(defn start
  "Return a running context with values that can be closed."
  [config]
  (closeable-hash-map
    :server (http/start-server (api context) {:port 3030})
    :producer (kafka-producer config)
    :consumer (kafka-consumer config)))

```

## Technicalities

Some Clojure datastructures implement `IFn`:

``` clojure
({:a 1} :a) ;; => 1
(remove #{:a} [:a :b :c]) ;; => '(:b :c)
([:a :b :c] 1) ;; => :b
```

Clojure maps (`IPersistentMap`) implement `IFn`, for `invoke()` of one
argument (a key) with an optional second argument (a default value),
i.e. maps are functions of their keys. `nil` keys and values are fine.

This library defines a new data strucure, CloseableMap. It is exposed
as an instance of `java.io.Closeable` which is a subinterface of
`java.lang.AutoCloseable`. When trying to close its values, it looks
for instances of the latter. As such, it tries to be most general.

``` clojure
(require '[clojure.data])

(clojure.data/diff
  (ancestors (class {}))
  (ancestors CloseableMap))

;; =>
[;; Ancestors of Clojure map only but not CloseableMap.
 #{clojure.lang.AFn ; Concrete type, but see below for IFn.
   clojure.lang.APersistentMap
   clojure.lang.IEditableCollection
   clojure.lang.IKVReduce
   clojure.lang.IMapIterable
   java.io.Serializable}

 ;; Ancestors of CloseableMap only.
 #{clojure.lang.IType
   java.io.Closeable
   java.lang.AutoCloseable
   java.util.Iterator
   potemkin.collections.PotemkinMap
   potemkin.types.PotemkinType}

 ;; Ancestors common to both types.
 #{clojure.lang.Associative
   clojure.lang.Counted
   clojure.lang.IFn
   clojure.lang.IHashEq
   clojure.lang.ILookup
   clojure.lang.IMeta
   clojure.lang.IObj
   clojure.lang.IPersistentCollection
   clojure.lang.IPersistentMap
   clojure.lang.MapEquivalence
   clojure.lang.Seqable
   java.lang.Iterable
   java.lang.Object
   java.lang.Runnable
   java.util.Map
   java.util.concurrent.Callable}]
```
