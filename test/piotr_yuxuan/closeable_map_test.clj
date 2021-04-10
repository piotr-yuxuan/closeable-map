(ns piotr-yuxuan.closeable-map-test
  (:require [clojure.test :refer [deftest testing is]]
            [piotr-yuxuan.closeable-map :refer [closeable-map closeable-hash-map]])
  (:import (clojure.lang ExceptionInfo)
           (java.lang AutoCloseable)
           (java.io Closeable)))

(deftest closeable-map-test
  (testing "idempotent: close nothing when nothing is open"
    (let [log (atom [])]
      (swap! log conj :before)
      (with-open [m (closeable-map {:no :op})]
        (is (= m {:no :op}))
        (swap! log conj :with-open))
      (swap! log conj :after)
      (is (= [:before :with-open :after] @log))))

  (testing "preserve existing meta"
    (with-open [m (closeable-map (with-meta {:no :op} {:a 1}))]
      (is (= m {:no :op}))
      (is (= {:a 1} (meta m)))))

  (testing "preserve explicit meta"
    (with-open [m (closeable-map (with-meta {:no :op} {:a 1}) {:b 2})]
      (is (= m {:no :op}))
      (is (= {:b 2} (meta m)))))

  (testing "function is called"
    (let [log (atom [])]
      (swap! log conj :before)
      (with-open [m (closeable-map {:inner-log log
                                    :close (fn [m]
                                             ;; self-referential
                                             (swap! (:inner-log m) conj :on-close))})]
        (swap! log conj :with-open)
        ;; with-open only allows Symbols in bindings
        ;; See https://github.com/jarohen/with-open
        ;; circumvent that limitation.
        (swap! (:inner-log m) conj :open-reference))
      (swap! log conj :after)
      (is (= [:before :with-open :open-reference :on-close :after] @log))))

  (testing "collection of functions: called in order"
    (let [log (atom [])]
      (swap! log conj :before)
      (with-open [_ (closeable-map {:close [(fn [_] (swap! log conj :on-close/first))
                                            (fn [_] (swap! log conj :on-close/second))
                                            (fn [_] (swap! log conj :on-close/third))]})]
        (swap! log conj :with-open))
      (swap! log conj :after)
      (is (= [:before :with-open :on-close/first :on-close/second :on-close/third :after] @log))))

  (testing "throws an exception when incorrect value type"
    (let [caught (atom nil)]
      (try (with-open [_ (closeable-map {:close :lolipop})])
           (catch ExceptionInfo ex
             (reset! caught ex)))
      (is (= "close must be a function, or a sequence of functions" (ex-message @caught)))))

  (testing "with no explicit `:close`, close all values that are instances of `AutoCloseable`"
    (let [log (atom [])]
      (swap! log conj :before)
      (with-open [_ (closeable-map {:closeable (reify Closeable (close [_] (swap! log conj :on-close/closeable)))
                                    :auto-closeable (reify AutoCloseable (close [_] (swap! log conj :on-close/auto-closeable)))
                                    :other :smurf})]
        (swap! log conj :with-open))
      (swap! log conj :after)
      (is (= [:before :with-open :on-close/closeable :on-close/auto-closeable :after] @log))))

  (testing "syntactic sugar, tail of interleaved keys and values"
    (let [log (atom [])]
      (swap! log conj :before)
      (with-open [_ (closeable-hash-map
                      :closeable (reify Closeable (close [_] (swap! log conj :on-close/closeable)))
                      :auto-closeable (reify AutoCloseable (close [_] (swap! log conj :on-close/auto-closeable)))
                      :other :pastry)]
        (swap! log conj :with-open))
      (swap! log conj :after)
      (is (= [:before :with-open :on-close/closeable :on-close/auto-closeable :after] @log)))))
