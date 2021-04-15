(ns piotr-yuxuan.closeable-map
  (:require [clojure.data]
            [clojure.walk :as walk]
            [potemkin :refer [def-map-type]])
  (:import (java.io Closeable)
           (java.lang AutoCloseable)
           (java.util Map)))

(defn close!
  "FIXME cljdoc"
  [form]
  ;; AutoCloseable is a superinterface of Closeable.
  (cond (instance? AutoCloseable form) (.close ^AutoCloseable form)
        (::fn (meta form)) (let [thunk form] (thunk))))

(def visitor
  "FIXME cljdoc"
  ;; No checks on closing functions. You may pass a keyword for advanced use case with lazy maps.
  (letfn [(before-close [x] (when-let [close! (or (and (instance? Map x)
                                                       (::before-close x))
                                                  (::before-close (meta x)))]
                              (close! x)))
          (after-close [x] (when-let [close! (or (and (instance? Map x)
                                                      (::after-close x))
                                                 (::after-close (meta x)))]
                             (close! x)))
          #_(swallow [x f]
                     (if (get (meta x) ::swallow)
                       (try (f x)
                            (catch Throwable _)
                            (finally x))
                       (doto x f)))
          (ignore [x] (when-not (get (meta x) ::ignore) x))]
    (fn visitor [form]
      (walk/walk visitor
                 #(doto %
                    close!
                    after-close)
                 (doto (ignore form)
                   before-close)))))

(def-map-type CloseableMap [m mta]
  (get [_ k default-value] (get m k default-value))
  (assoc [_ k v] (CloseableMap. (assoc m k v) mta))
  (dissoc [_ k] (CloseableMap. (dissoc m k) mta))
  (keys [_] (keys m))
  (meta [_] mta)
  (with-meta [_ mta] (CloseableMap. m mta))

  Closeable ;; Closeable is a subinterface of AutoCloseable.
  (^void close [this] (visitor m) nil))

(defn ^Closeable closeable-map
  [m]
  {:pre [(instance? Map m)]}
  (CloseableMap. m (meta m)))
