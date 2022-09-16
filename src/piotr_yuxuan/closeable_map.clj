(ns piotr-yuxuan.closeable-map
  ;; Manually keep this description in sync with relevant parts of the README.
  "In your project, require:

``` clojure
(require '[piotr-yuxuan.closeable-map :as closeable-map :refer [close-with with-tag]])
```

Define an application that can be started, and closed.

``` clojure
(defn start
  \"Return a map describing a running application, and which values may
  be closed.\"
  [config]
  (closeable-map/closeable-map
    {;; Kafka producers/consumers are `java.io.Closeable`.
     :producer (kafka-producer config)
     :consumer (kafka-consumer config)}))
```

You can start/stop the app in the repl with:

``` clojure
(comment
  (def config (load-config))
  (def system (start config))

  ;; Stop/close all processes/resources with:
  (.close system)
  )
```

It can be used in conjunction with `with-open` in test file to create
well-contained, independent tests:

``` clojure
(with-open [{:keys [consumer] :as app} (start config)]
  (testing \"unit test with isolated, repeatable context\"
    (is (= :yay/🚀 (some-business/function consumer)))))
```

You could also use thi library while live-coding to stop and restart
your application whenever a file is changed.

## More details

``` clojure
(defn start
  \"Return a map describing a running application, and which values may
  be closed.\"
  [config]
  (closeable-map/closeable-map
    {;; Kafka producers/consumers are `java.io.Closeable`.
     :producer (kafka-producer config)
     :consumer (kafka-consumer config)

     ;; File streams are `java.io.Closeable` too:
     :logfile (io/output-stream (io/file \"/tmp/log.txt\"))

     ;; Closeable maps can be nested. Nested maps will be closed before the outer map. 
     :backend/api {:response-executor (close-with (memfn ^ExecutorService .shutdown)
                                        (flow/utilization-executor (:executor config)))
                   :connection-pool (close-with (memfn ^IPool .shutdown)
                                      (http/connection-pool {:pool-opts config}))

                   ;; These functions receive their map as argument.
                   ::closeable-map/before-close (fn [m] (backend/give-up-leadership config m))
                   ::closeable-map/after-close (fn [m] (backend/close-connection config m))}

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

When `(.close system)` is executed, it will:

  - Recursively close all instances of `java.io.Closeable` and
    `java.lang.AutoCloseable`;
  - Recursively call all stop zero-argument functions tagged with
    `^::closeable-map/fn`;
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
       ::closeable-map/after-close (fn [this-map] (garbage/collect!))})
    ```

Some classes do not implement `java.lang.AutoCloseable` but present
some similar method. For example instances of
`java.util.concurrent.ExecutorService` can't be closed but they can be
`.shutdown`:

``` clojure
{:response-executor (close-with (memfn ^ExecutorService .shutdown)
                      (flow/utilization-executor (:executor config)))
 :connection-pool (close-with (memfn ^IPool .shutdown)
                    (http/connection-pool {:pool-opts config}))}
```

You may also extend this library by giving new dispatch values to
multimethod [[piotr-yuxuan.closeable-map/close!]]. Once evaluated,
this will work accross all your code. The multimethod is dispatched on
the concrete class of its argument:

``` clojure
(import '(java.util.concurrent ExecutorService))
(defmethod closeable-map/close! ExecutorService
  [x]
  (.shutdown ^ExecutorService x))

(import '(io.aleph.dirigiste IPool))
(defmethod closeable-map/close! IPool
  [x]
  (.shutdown ^IPool x))
```

## All or nothing

### No half-broken closeable map

You may also avoid partially open state when an exception is thrown
when creating a `CloseableMap`. This is where `closeable-map*` comes
handy. It outcome in one of the following:

- Either everything went right, and all inner forms wrapped by
  `closeable` correctly return a value; you get an open instance of `CloseableMap`.

- Either some inner form wrapped by `closeable` didn't return a
  closeable object but threw an exception instead. Then all
  `closeable` forms are closed, and finally the exception is
  bubbled up.

``` clojure
(closeable-map*
  {:server (closeable* (http/start-server (api config)))
   :kafka {:consumer (closeable* (kafka-consumer config))
           :producer (closeable* (kafka-producer config))
           :schema.registry.url \"https://localhost\"}})
```

### No half-broken state in general code

In some circumstances you may need to handle exception on the creation
of a closeable map. If an exception happens during the creation of the
map, values already evaluated will be closed. No closeable objects
will be left open with no references to them.

For instance, this form would throw an exception:

``` clojure
(closeable-map/closeable-map {:server (http/start-server (api config))
                              :kafka {:consumer (kafka-consumer config)
                                      :producer (throw (ex-info \"Exception\" {}))}})
;; => (ex-info \"Exception\" {})
```

`with-closeable*` prevents that kind of broken, partially open states for its bindings:

``` clojure
(with-closeable* [server (http/start-server (api config))
                  consumer (kafka-consumer config)
                  producer (throw (ex-info \"Exception\" {}))]
  ;; Your code goes here.
)
;; Close consumer,
;; close server,
;; finally throw `(ex-info \"Exception\" {})`.
```

You now have the guarantee that your code will only be executed if
all these closeable are open. In the latter example an exception is
thrown when `producer` is evaluated, so `consumer` is closed, then
`server` is closed, and finally the exception is bubbled up. Your
code is not evaluated. In the next example the body is evaluated,
but throws an exception: all bindings are closed.

``` clojure
(with-closeable* [server (http/start-server (api config))
                  consumer (kafka-consumer config)
                  producer (kafka-producer config)]
  ;; Your code goes here.
  (throw (ex-info \"Exception\" {})))
;; Close producer,
;; close consumer,
;; close server,
;; finally throw `(ex-info \"Exception\" {})`.
```

When no exception is thrown, leave bindings open and return like a
normal `let` form. If you prefer to close bindings, use `with-open` as
usual.
  
``` clojure
(with-closeable* [server (http/start-server (api config))
                  consumer (kafka-consumer config)
                  producer (kafka-producer config)]
  ;; Your code goes here.
  )
;; All closeable in bindings stay open.
;; => result
```"
  (:require [clojure.data]
            [clojure.walk :as walk]
            [potemkin :refer [def-map-type]])
  (:import (java.io Closeable)
           (java.lang AutoCloseable)
           (java.util Map)
           (clojure.lang IObj)))

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

(def ^:dynamic *closeables*
  "FIXME cljdoc"
  nil)

(defmulti close!
  "Perform a side effect of the form `x` passed as argument and attempts
  to close it. If it doesn't know how to close it, does
  nothing. Functions tagged with `^::fn` are considered closeable as
  `java.io.Closeable` and `java.lang.AutoCloseable`.

  This multimethod is dispatched on the concrete class of its
  argument. You can extend this method like any other multimethod.

  ``` clojure
  (import '(java.util.concurrent ExecutorService))
  (defmethod closeable-map/close! ExecutorService
    [x]
    (.shutdown ^ExecutorService x))

  (import '(io.aleph.dirigiste IPool))
  (defmethod closeable-map/close! IPool
    [x]
    (.shutdown ^IPool x))
  ```"
  class)

(defmethod close! :default
  [x]
  (let [fn-tag (::fn (meta x))]
    ;; AutoCloseable is a superinterface of Closeable.
    (cond (instance? AutoCloseable x) (.close ^AutoCloseable x)
          (true? fn-tag) (x)
          fn-tag (fn-tag x))))

(defn -close-*closeables*
  [*closeables*]
  (doseq [c @*closeables*]
    (try (close! c)
         (catch Throwable _)
         (finally (swap! *closeables* rest)))))

(def visitor
  "Take a form `x` as one argument and traverse it while trying to
  [[piotr-yuxuan.closeable-map/close!]] inner items.

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
                         (before-close x swallow)
                         (when-let [*closeables* (::*closeables* (meta x))]
                           (-close-*closeables* *closeables*)))
                       x))))))

(def-map-type CloseableMap [m mta]
  (get [_ k default-value] (get m k default-value))
  (assoc [_ k v] (CloseableMap. (assoc m k v) mta))
  (dissoc [_ k] (CloseableMap. (dissoc m k) mta))
  (keys [_] (keys m))
  (meta [_] mta)
  (with-meta [_ mta] (CloseableMap. m mta))

  Closeable ;; Closeable is a subinterface of AutoCloseable.
  ;; `this` has `mta` as meta whilst may have no meta
  (^void close [this] (visitor this)))

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
    \"The code is the docstring: [truncated]\"
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

(defmacro close-with
  "Take a procedure `proc`, an object `x`, return `x`. When the map is
  closed `(proc x)` will be called. Will have no effect out of a
  closeable map.

  Some classes do not implement `java.lang.AutoCloseable` but present
  some similar method. For example instances of
  `java.util.concurrent.ExecutorService` can't be closed but they can
  be shut down, which achieves a similar outcome. This convenience
  macro allows you to express it this way:

  ``` clojure
  (closeable-map/close-with (memfn ^ExecutorService .shutdown) my-service)
  ;; => my-service
  ```"
  [proc x]
  `(vary-meta ~x assoc ::fn ~proc))

(defmacro closeable*
  "Use it within `closeable-map*` or `with-closeable*` to avoid
  partially open state when an exception is thrown on evaluating
  closeable forms.

  When `x` is a Java object, store as a object that may be closed. You
  may then extend the multimethod `close!` to provide a standard way
  to close this class of objects."
  [x]
  `(do (assert *closeables* "`closeable` should only be used within `closeable-map*` or `with-closeable*`.")
       (let [ret# ~x]
         (swap! *closeables* conj ret#)
         (if (instance? IObj ret#)
           (with-tag ::ignore ret#)
           ret#))))

(defmacro with-closeable*
  "Take two arguments, a `bindings` vector and a `body`. Like a `let`,
  support destructuring. Avoids partially open state when an exception
  is thrown on evaluating closeable forms. Evaluate bindings
  sequentially then return `body` and leave bindings open. When an
  exception is thrown in a later binding or in the `body`, close
  bindings already open in reverse order and finally bubble up the
  exception. Do nothing for non-closeable bindings.

  Use it if you need exception handling on the creation of a closeable
  map, so no closeable objects are left open but with no references
  because of an exception.

  For instance, this form would throw an exception and leave the
  server open and the port locked:

  ``` clojure
  (closeable-map {:server (http/start-server (api config))
                  :kafka {:consumer (kafka-consumer config)
                          :producer (throw (ex-info \"Exception\" {}))}})
  ;; `consumer` and `server` stay open but with no reference. Kafka
  ;; messages are consumed and the port is locked.
  ;; => (ex-info \"Exception\" {})
  ```

  `with-closeable*` prevents that kind of broken, partially open
  states for its bindings:

  ``` clojure
  (with-closeable* [server (http/start-server (api config))
                    consumer (kafka-consumer config)
                    producer (throw (ex-info \"Exception\" {}))]
    ;; Your code goes here.
  )
  ;; Close consumer,
  ;; close server,
  ;; finally throw `(ex-info \"Exception\" {})`.
  ```

  You now have the guarantee that your code will only be executed if
  all these closeable are open. In the latter example an exception is
  thrown when `producer` is evaluated, so `consumer` is closed, then
  `server` is closed, and finally the exception is bubbled up. Your
  code is not evaluated. In the next example the body is evaluated,
  but throws an exception: all bindings are closed.

  ``` clojure
  (with-closeable* [server (http/start-server (api config))
                    consumer (kafka-consumer config)
                    producer (kafka-producer config)]
    ;; Your code goes here.
    (throw (ex-info \"Exception\" {})))
  ;; Close producer,
  ;; close consumer,
  ;; close server,
  ;; finally throw `(ex-info \"Exception\" {})`.
  ```

  When no exception is thrown, leave bindings open and return like a
  normal `let` form.
  
  ``` clojure
  (with-closeable* [server (http/start-server (api config))
                    consumer (kafka-consumer config)
                    producer (kafka-producer config)]
    ;; Your code goes here.
    )
  ;; All closeable in bindings stay open.
  ;; => result
  ```"
  [bindings & body]
  (assert (even? (count bindings)) "Expecting an even number of forms in `bindings`.")
  (if (zero? (count bindings))
    `(binding [*closeables* (or *closeables* (atom ()))]
       ~@body)
    (let [v (gensym "value")]
      `(binding [*closeables* (or *closeables* (atom ()))]
         (try
           (let [~(nth bindings 0) ~(nth bindings 1) ; unsplice?
                 ~v ~(nth bindings 0)]
             (swap! *closeables* conj ~v)
             (with-closeable* ~(subvec bindings 2) ~@body))
           (catch Throwable th#
             (-close-*closeables* *closeables*)
             (throw th#)))))))

(defmacro ^CloseableMap closeable-map*
  "Avoid partially open state when an exception is thrown on evaluating
  inner closeable forms wrapped by `closeable`. Inner forms must
  return a map.

  ``` clojure
  (closeable-map*
    {:server (closeable* (http/start-server (api config)))
     :kafka {:consumer (closeable* (kafka-consumer config))
             :producer (closeable* (kafka-producer config))
             :schema.registry.url \"https://localhost\"}})
  ```

  The outcome of this macro `closeable-map*` is one of the following:

  - Either everything went right, and all inner forms wrapped by
    `closeable` correctly return a value, then this macro returns an
    open instance of `CloseableMap`.

  - Either some inner form wrapped by `closeable` didn't return a
    closeable object but threw an exception instead. Then all
    `closeable` forms are closed, and finally the exception is
    bubbled up.

  Known (minor) issue: type hint is not acknowledged, you have to tag
  it yourself with `^CloseableMap` (most precise),
  `^java.io.Closeable`, or `^java.lang.AutoCloseable` (most generic)."
  [& body]
  `(binding [*closeables* (or *closeables* (atom ()))]
     (try (vary-meta (closeable-map (do ~@body)) assoc
                     :tag `CloseableMap
                     ::*closeables* *closeables*)
          (catch Throwable th#
            (doseq [c# @*closeables*]
              (try (close! c#)
                   (catch Throwable _#)
                   (finally (swap! *closeables* rest))))
            (throw th#)))))
