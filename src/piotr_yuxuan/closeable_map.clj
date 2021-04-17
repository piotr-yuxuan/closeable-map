(ns piotr-yuxuan.closeable-map
  "In your project, require:

``` clojure
(require '[piotr-yuxuan.closeable-map :as closeable-map :refer [with-tag]])
```

Then you can define an application that can be started, and closed.

``` clojure
(defn start
  \"Return a running context with values that can be closed.\"
  [config]
  (closeable-map/closeable-map
    {;; Kafka producers/consumers are `java.io.Closeable`.
     :producer (kafka-producer config)
     :consumer (kafka-consumer config)

     ;; Closeable maps can be nested.
     :backend/api {:response-executor (flow/utilization-executor (:executor config))
                   :connection-pool (http/connection-pool {:pool-opts config})

                   ;; File streams are `java.io.Closeable` too:
                   :logfile (io/output-stream (io/file \"/tmp/log.txt\"))

                   ;; This will be called as final closing step for
                   ;; this nested map backend/api. See also
                   ;; `::closeable-map/before-close` which is called
                   ;; before closing a map.
                   ::closeable-map/after-close
                   (fn [m]
                     ;; Some classes have similar semantic, but do not
                     ;; implement `java.io.Closeable`. We can handle
                     ;; them anyway.
                     (.shutdown ^ExecutorService (:response-executor m))
                     (.shutdown ^IPool (:connection-pool m)))}

     ;; Any exception when closing this nested map will be swallowed
     ;; and not bubbled up.
     :db ^::closeable-map/swallow {;; Connection are `java.io.Closeable`, too:
                                   :db-conn (jdbc/get-connection (:db config))}

     ;; Some libs return a zero-argument function which when called
     ;; stops the server, like:
     :server (with-tag ::closeable-map/fn (http/start-server (api config) (:server config)))
     ;; Gotcha: Clojure meta data can only be attached on 'concrete'
     ;; objects; they are lost on literal forms (see above).
     :forensic ^::closeable-map/fn #(metrics/report-death!)

     ::closeable-map/ex-handler
     (fn [ex]
       ;; Will be called for all exceptions thrown when closing this
       ;; map and nested items.
       (println (ex-message ex)))}))
```

Then you can start/stop the app in the repl with:

``` clojure
(comment
  (def config (load-config))
  (def system (start config))

  ;; Stop/close all processes/resources with:
  (.close system)
)
```

You can use it in conjunction with `with-open` like in test file:

``` clojure
(with-open [system (start config)]
  (testing \"unit test with isolated, repeatable context\"
    (is (= :yay/🚀 (some-business/function context)))))
```

When `(.close system)` is executed, it will:

  - Recursively close all instances of `java.io.Closeable` and `java.lang.AutoCloseable`;

  - Recursively call all stop zero-argument functions tagged with `^::closeable-map/fn`;

  - Skip all nested `Closeable` under a `^::closeable-map/ignore`;

  - Silently swallow any exception with `^::closeable-map/swallow`;

  - Exceptions to optional `::closeable-map/ex-handler` in key or
    metadata;

  - If keys (or metadata) `::closeable-map/before-close` or
    `::closeable-map/after-close` are present, they will be assumed as
    a function which takes one argument (the map itself) and used run
    additional closing logic:

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

  - You can easily extend this library by giving new dispatch values
    to multimethod {{piotr-yuxuan.closeable-map/close!}. It is
    dispatched on the concrete class of its argument.

    ``` clojure
    (import '(java.util.concurrent ExecutorService))
    (defmethod closeable-map/close! ExecutorService (memfn ^ExecutorService destroy))
    ```
"
  (:require [clojure.data]
            [clojure.walk :as walk]
            [potemkin :refer [def-map-type]])
  (:import (java.io Closeable)
           (java.lang AutoCloseable)
           (java.util Map)))

(def ^:dynamic *swallow?*
  "Dynamic var. If bound to a logically true value in closing thread,
  will swallow any `java.lang.Throwable`, the apex class for all
  exceptions in Java and Clojure. You may change its value is some
  nested maps with meta `{::swallow false}`.

  Because `clojure.walk/walk` is used for map traversal, it is not
  possible to pass down any argument. Also, as we iteratively walk
  through nested data structures, some of them do not support
  metadata. As a result, a binding on this dynamic var in the
  execution thread enables a simple way to remember the parent value
  as we visit the children."
  false)

(def ^:dynamic *?ex-handler*
  "Dynamic var. If non-nil, will be invoked with the exception as
  argument A one-argument function is excepted.

  Because `clojure.walk/walk` is used for map traversal, it is not
  possible to pas s down any argument. Also, as we iteratively walk
  through nested data structures, some of them do not support
  metadata. As a result, a binding on this dynamic var in the
  execution thread enables a simple way to remember the parent value
  as we visit the children."
  nil)

(def ^:dynamic *ignore?*
  "Dynamic var. If bound to a logically true value in closing thread,
  will ignore any closeable items. You may change its value is some
  nested maps with meta `{::ignore false}`.
  
  Because `clojure.walk/walk` is used for map traversal, it is not
  possible to pass down any argument. Also, as we iteratively walk
  through nested data structures, some of them do not support
  metadata. As a result, a binding on this dynamic var in the
  execution thread enables a simple way to remember the parent value
  as we visit the children."
  false)

(defmulti close!
  "Perform a side effect of the form `x` passed as argument and attempts
  to close it. If it doesn't know how to close it, does
  nothing. Functions tagged with `^::fn` are considered closeable as
  `java.io.Closeable` and `java.lang.AutoCloseable`.

  This multimethod is dispatched on the concrete class of its
  argument. You can extend this method like any other multimethod.

  ```
  (import '(java.util.concurrent ExecutorService))

  (defmethod closeable-map/close! ExecutorService (memfn ^ExecutorService destroy))
  ```"
  class)

(defmethod close! :default
  [x]
  ;; AutoCloseable is a superinterface of Closeable.
  (cond (instance? AutoCloseable x) (.close ^AutoCloseable x)
        (::fn (meta x)) (x)))

(def visitor
  "Take a form `x` as one argument and traverse it while trying to
  {{close!}} inner items.

  Map keys `::before-close` and `::after-close` will be invoked before
  and after other keys of the map. Will ignore items marked with
  `^::ignore`. Exceptions when closing some item will be passed to an
  optional `::ex-handler`. With `^::swallow` they will not be raised
  higher and will stay silently swallowed. Functions tagged with
  `^::fn` will be considered closeable and will thus be called on
  `.close`.

  For advance usage, no checks on closing functions, the minimal
  requirement is they be invokable. Map keys can also be tagged. Maps
  values `::before-close` and `::after-close` are not expected to be
  tagged with `^::fn` for they would be executed twice."
  (letfn [(before-close [x f] (when-let [close! (or (and (instance? Map x)
                                                         (::before-close x))
                                                    (::before-close (meta x)))]
                                (f x close!)))
          (after-close [x f] (when-let [close! (or (and (instance? Map x)
                                                        (::after-close x))
                                                   (::after-close (meta x)))]
                               (f x close!)))
          (?ex-handler [x] (cond (contains? (meta x) ::ex-handler) (::ex-handler (meta x))
                                 (and (instance? Map x) (contains? x ::ex-handler)) (::ex-handler x)
                                 :else *?ex-handler*))
          (swallow? [x] (cond (contains? (meta x) ::swallow) (::swallow (meta x))
                              (and (instance? Map x) (contains? x ::swallow?)) (::swallow? x)
                              :else *swallow?*))
          (swallow [x f] (if (or (swallow? x)
                                 (swallow? f))
                           (try (f x)
                                (catch Throwable th
                                  (when (?ex-handler x)
                                    (*?ex-handler* th)))
                                (finally x))
                           (doto x f)))
          (ignore? [x] (cond (contains? (meta x) ::ignore) (::ignore (meta x))
                             (and (instance? Map x) (contains? x ::ignore?)) (::ignore? x)
                             :else *ignore?*))]
    (fn visitor
      [x]
      (binding [*swallow?* (swallow? x)
                *ignore?* (ignore? x)
                *?ex-handler* (?ex-handler x)]
        (walk/walk visitor
                   #(do (when-not (ignore? %)
                          (swallow % close!)
                          (after-close % swallow))
                        %)
                   (do (when-not (ignore? x)
                         (before-close x swallow))
                       x))))))

(def-map-type CloseableMap [m mta]
  (get [_ k default-value] (get m k default-value))
  (assoc [_ k v] (CloseableMap. (assoc m k v) mta))
  (dissoc [_ k] (CloseableMap. (dissoc m k) mta))
  (keys [_] (keys m))
  (meta [_] mta)
  (with-meta [_ mta] (CloseableMap. m mta))

  Closeable ;; Closeable is a subinterface of AutoCloseable.
  (^void close [this] (visitor m)))

(defn ^CloseableMap closeable-map
  "Take any object that implements a map interface and return a new map
  that can later be closed. You may use this map like any other map
  for example with `update` or `assoc`. When you call `(.close m)`,
  inner closeable items will be closed.

  Map keys `::before-close` and `::after-close` will be evaluated
  before and after other keys of the map. Will ignore items marked
  with `^::ignore`, and exceptions thrown under `^::swallow` will be
  silently swallowed. Functions tagged with `^::fn` will be considered
  closeable. No checks on closing functions, the minimal requirement
  is they be invokable."
  [m]
  {:pre [(instance? Map m)]}
  (CloseableMap. m (meta m)))

(def ^:constant ^CloseableMap empty-map
  "An empty, immutable closeable map that you may use like any other
  map. When you call `(.close m)` on it, inner closeable items will be
  closed."
  (closeable-map {}))

(def ^:constant ^CloseableMap swallowed
  "An empty, immutable closeable map that swallows all exceptions. A
  nested closeable item may throw an exception by setting its metadata
  to `{::swallow false}`. You may use it like any other map."
  (closeable-map ^::swallow {}))

(def ^:constant ^CloseableMap ignored
  "An empty, immutable closeable map that ignores all closeable. A
  nested closeable may be no longer ignored if its metadata contain
  `{::ignore false}`. You may use it like any other map."
  (closeable-map ^::ignore {}))

(defmacro with-tag-
  "The code is the docstring:
  ``` clojure
  (defmacro -with-tag
    \"The code is the docstring:\"
    [x tag]
    `(vary-meta ~x assoc ~tag true))
  ```"
  [x tag]
  `(vary-meta ~x assoc ~tag true))

(defn with-tag
  "By design, the Clojure shortcut notation `^::closeable-map/fn {}`
  works only on direct objects, not on bindings, or literal forms. use
  this function to circumvent this limitation.

  ``` clojure
  (meta
    (let [a {}]
      ^::closeable-map/fn a))
  ;; => nil

  (meta
    (let [a {}]
      (with-tag ::closeable-map/fn a)))
  ;; => #:piotr-yuxuan.closeable-map{:fn true}
  ```"
  [tag x]
  (with-tag- x tag))
