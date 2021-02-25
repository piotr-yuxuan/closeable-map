(ns piotr-yuxuan.closeable-map
  (:require [potemkin :refer [def-map-type]])
  (:import (java.io Closeable)))

(def-map-type CloseableMap [m mta]
  (get [_ k default-value] (get m k default-value))
  (assoc [_ k v] (CloseableMap. (assoc m k v) mta))
  (dissoc [_ k] (CloseableMap. (dissoc m k) mta))
  (keys [_] (keys m))
  (meta [_] mta)
  (with-meta [_ mta] (CloseableMap. m mta))

  Closeable
  (close [_] (when (contains? m :close)
               (let [close (:close m)]
                 (cond (sequential? close) (doseq [c close] (c m))
                       (fn? close) (close m)
                       :else (throw (ex-info "close must be a function, or a sequence of functions" {:m m, :mta mta})))))))

(defn closeable-map
  (^Closeable [m] (closeable-map m {}))
  (^Closeable [m mta] (CloseableMap. m mta)))
