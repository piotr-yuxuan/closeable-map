(ns piotr-yuxuan.closeable-map-test
  (:require [clojure.test :refer [deftest testing is]]
            [piotr-yuxuan.closeable-map :refer [closeable-map]])
  (:import (clojure.lang ExceptionInfo)))

(deftest closeable-map-test
  (testing "idempotent: close nothing if nothing open"
    (let [log (atom [])]
      (swap! log conj :before)
      (with-open [_ (closeable-map {})]
        (swap! log conj :with-open))
      (swap! log conj :after)
      (is (= [:before :with-open :after] @log))))

  (testing "function is called"
    (let [log (atom [])]
      (swap! log conj :before)
      (with-open [open-m (closeable-map {:inner-log log
                                         :close (fn [m]
                                                  ;; self-referential
                                                  (swap! (:inner-log m) conj :on-close))})]
        (swap! log conj :with-open)
        ;; with-open only allows Symbols in bindings
        ;; See https://github.com/jarohen/with-open
        ;; circumvent that limitation.
        (swap! (:inner-log open-m) conj :open-reference))
      (swap! log conj :after)
      (is (= [:before :with-open :open-reference :on-close :after] @log))))

  (testing "collection of functions: called in order"
    (let [log (atom [])]
      (swap! log conj :before)
      (with-open [open-m (closeable-map {:close [(fn [_] (swap! log conj :on-close/first))
                                                 (fn [_] (swap! log conj :on-close/second))
                                                 (fn [_] (swap! log conj :on-close/third))]})]
        (swap! log conj :with-open))
      (swap! log conj :after)
      (is (= [:before :with-open :on-close/first :on-close/second :on-close/third :after] @log))))

  (testing "throws an exception when incorrect value type"
    (let [caught (atom nil)]
      (try (with-open [open-m (closeable-map {:close nil})])
           (catch ExceptionInfo ex
             (reset! caught ex)))
      (is (= "close must be a function, or a sequence of functions" (ex-message @caught))))))
