# `closeable-map`

[![](https://img.shields.io/clojars/v/piotr-yuxuan/closeable-map.svg)](https://clojars.org/piotr-yuxuan/closeable-map)
[![cljdoc badge](https://cljdoc.org/badge/piotr-yuxuan/closeable-map)](https://cljdoc.org/d/piotr-yuxuan/closeable-map/CURRENT)
[![GitHub license](https://img.shields.io/github/license/piotr-yuxuan/closeable-map)](https://github.com/piotr-yuxuan/closeable-map/blob/main/LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/piotr-yuxuan/closeable-map)](https://github.com/piotr-yuxuan/closeable-map/issues)

This small library defines a new type of Clojure that you can
close. It is a tiny alternative to more capable projects:
- Application state management:
  [stuartsierra/component](https://github.com/stuartsierra/component),
  [weavejester/integrant](weavejester/integrant),
  [tolitius/mount](https://github.com/tolitius/mount), _et al_.
- Extension of `with-open`:
  [jarohen/with-open](https://github.com/jarohen/with-open)
- Representing state in a map:
  [robertluo/fun-map](https://github.com/robertluo/fun-map)

``` clojure
;; In your project, require:
(require '[piotr-yuxuan.closeable-map :as closeable-map])

(defn start
  "Return a running context with values that can be closed."
  [config]
  (closeable-map/closeable-map
    {;; Kafka producers/consumers are `java.io.Closeable`.
     :producer (kafka-producer config)
     :consumer (kafka-consumer config)

     ;; File streams are `java.io.Closeable` too:
     :outfile (io/output-stream (io/file "/tmp/outfile.txt"))

     ;; Closeable-maps can be nested.
     :db {:db-conn (jdbc/get-connection (:db config))}

     ;; Some libs return a function which when called stop the server, like:
     :server ^::closeable-map/fn (http/start-server (api config) (:server config))}))


;; Then you can start/stop the app in the repl with:
(comment
  (def config (load-config))
  (def system (start config))

  ;; Stop/close all processes/resources with:
  (.close system)
)


;; You can use it in conjunction with `with-open` like in test file:
(with-open [system (start config)]
  (testing "unit test with isolated, repeatable context"
    (is (= :yay/🚀 (some-business/function context)))))
```

When `(.close system)` is executed, it will:

  - Recursively close all instances of `java.io.Closeable` and `java.lang.AutoCloseable`;
  - Recursively call all stop functions tagged with `^::closeable-map/fn`;
  - Skip all `Closeable` tagged with `^::closeable-map/ignore`;
  - If keys `::closeable-map/before-close` or `::closeable-map/before-close` are present, they will be assumed
    as a function which takes one argument (the map itself) and used
    run additional closing logic.
    ``` clojure
    (closeable-map
      {;; This function will be executed before the auto close.
       ::closeable-map/before-close (fn [this-map] (flush!))

       ;; Kafka producers/consumers are java.io.Closeable
       :producer (kafka-producer config)
       :consumer (kafka-consumer config)

       ;; This function will be executed after the auto close.
       ::closeable-map/after-close (fn [this-map] (garbage/collect!))
       }
    )
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
