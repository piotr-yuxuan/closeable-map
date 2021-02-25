(ns user
  (:require [piotr-yuxuan.closeable-map :refer [closeable-map]]))

(defn run-x
  [{:keys [arg]}]
  (-> arg
      closeable-map
      pr-str
      println))
