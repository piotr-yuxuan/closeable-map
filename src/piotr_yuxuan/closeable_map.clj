(ns piotr-yuxuan.closeable-map
  (:require [clojure.data]
            [clojure.walk :as walk]
            [potemkin :refer [def-map-type]])
  (:import (java.io Closeable)
           (java.lang AutoCloseable)
           (java.util Map)))

(defn close-if-closeable!
  [form]
  ;; AutoCloseable is a superinterface of Closeable.
  (cond (instance? AutoCloseable form) (.close ^AutoCloseable form)
        (::fn (meta form)) (let [thunk form] (thunk)))

  (when-let [m (and (instance? Map form) ^Map form)]
    (when-let [close (get m ::close)]
      (cond (sequential? close) (run! #(% m) close)
            (fn? close) (close m)
            :else (throw (ex-info "close must be a function, or a sequence of functions" {:m m})))))
  form)

(def-map-type CloseableMap [m mta]
  (get [_ k default-value] (get m k default-value))
  (assoc [_ k v] (CloseableMap. (assoc m k v) mta))
  (dissoc [_ k] (CloseableMap. (dissoc m k) mta))
  (keys [_] (keys m))
  (meta [_] mta)
  (with-meta [_ mta] (CloseableMap. m mta))

  Closeable ;; Closeable is a subinterface of AutoCloseable.
  (^void close [this] (walk/prewalk
                        (fn [form]
                          (when-not (::ignore (meta form))
                            (close-if-closeable! form)))
                        m)))

(defn ^Closeable closeable-map
  ([m] {:pre [(instance? Map {})]} (closeable-map m (meta m)))
  ([m mta] {:pre [(instance? Map {})]} (CloseableMap. m mta)))

(defn ^Closeable closeable-hash-map
  [& keyvals]
  {:pre [(even? (count keyvals))]}
  (closeable-map (apply hash-map keyvals)))
