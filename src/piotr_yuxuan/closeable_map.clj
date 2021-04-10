(ns piotr-yuxuan.closeable-map
  (:require [potemkin :refer [def-map-type]]
            [clojure.data])
  (:import (java.io Closeable)
           (java.lang AutoCloseable)
           (java.util Map)))

(def-map-type CloseableMap [m mta]
  (get [_ k default-value] (get m k default-value))
  (assoc [_ k v] (CloseableMap. (assoc m k v) mta))
  (dissoc [_ k] (CloseableMap. (dissoc m k) mta))
  (keys [_] (keys m))
  (meta [_] mta)
  (with-meta [_ mta] (CloseableMap. m mta))

  Closeable ;; Closeable is a subinterface of AutoCloseable.
  (close [_] (let [close (get m :close)]
               (cond (not (contains? m :close)) (->> (vals m)
                                                     ;; AutoCloseable is a superinterface of Closeable.
                                                     (filter (partial instance? AutoCloseable))
                                                     (run! (memfn ^AutoCloseable close)))
                     (sequential? close) (run! #(% m) close)
                     (fn? close) (close m)
                     :else (throw (ex-info "close must be a function, or a sequence of functions" {:m m, :mta mta}))))))

(defn ^Closeable closeable-map
  ([m] {:pre [(instance? Map {})]} (closeable-map m (meta m)))
  ([m mta] {:pre [(instance? Map {})]} (CloseableMap. m mta)))

(defn ^Closeable closeable-hash-map
  [& keyvals]
  {:pre [(even? (count keyvals))]}
  (closeable-map (apply hash-map keyvals)))

