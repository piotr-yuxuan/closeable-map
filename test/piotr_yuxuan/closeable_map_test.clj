(ns piotr-yuxuan.closeable-map-test
  (:require [clojure.test :refer [deftest testing is]]
            [piotr-yuxuan.closeable-map :refer [closeable-map closeable-hash-map] :as system])
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

  (testing "explicit meta are preserved, and replace existing meta"
    (with-open [m (closeable-map (with-meta {:no :op} {:a 1}) {:b 2})]
      (is (= m {:no :op}))
      (is (= {:b 2} (meta m)))))

  (testing "preserve values"
    (let [closed? (atom false)
          closeable (reify Closeable (close [_] (reset! closed? true)))
          control-m {:closed? closed?, :no :op, :closeable closeable}
          m (closeable-map control-m)]
      (is (= control-m m))
      (is (not @closed?))
      (with-open [open-m (closeable-map m)]
        (is (not @closed?))
        (is (= m open-m control-m)))
      (is @closed?)
      (is (= control-m m))))

  (testing "function is called"
    (let [log (atom [])]
      (swap! log conj :before)
      (with-open [m (closeable-map {:inner-log log
                                    ::system/on-close (fn [m]
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
      (with-open [_ (closeable-map {::system/on-close [(fn [_] (swap! log conj :on-close/first))
                                                       (fn [_] (swap! log conj :on-close/second))
                                                       (fn [_] (swap! log conj :on-close/third))]})]
        (swap! log conj :with-open))
      (swap! log conj :after)
      (is (= [:before :with-open :on-close/first :on-close/second :on-close/third :after] @log))))

  (testing "throws an exception when incorrect value type"
    (let [caught (atom nil)]
      (try (with-open [_ (closeable-map {::system/on-close :lolipop})])
           (catch ExceptionInfo ex
             (reset! caught ex)))
      (is (= "close must be a function, or a sequence of functions" (ex-message @caught)))))

  (testing "close all values that are instances of `AutoCloseable`"
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
      (is (= [:before :with-open :on-close/closeable :on-close/auto-closeable :after] @log))))

  (testing "forms with meta ^::closeable-map/fn are invoked as thunks (no-arg function), those with ::closeable-map/fn are ignored."
    (let [log (atom [])]
      (swap! log conj :before)
      (with-open [_ (closeable-map {:closeable (reify Closeable (close [_] (swap! log conj :on-close/closeable)))
                                    :ignored-closeable ^::system/ignore (reify Closeable (close [_] (swap! log conj :on-close/ignored-closeable)))
                                    :auto-closeable (reify AutoCloseable (close [_] (swap! log conj :on-close/auto-closeable)))
                                    :some-close-fn ^::system/fn (fn [] (swap! log conj :on-close/some-close-fn))
                                    :non-close-fn (fn [] (swap! log conj :on-close/non-close-fn))
                                    ::system/on-close (fn [_] (swap! log conj :on-close/explicit-close))
                                    :other :smurf})]
        (swap! log conj :with-open))
      (swap! log conj :after)
      (is (= @log
             [:before
              :with-open
              :on-close/explicit-close
              :on-close/closeable
              :on-close/auto-closeable
              :on-close/some-close-fn
              :after]))))

  (testing "recursively close nested state, using postwalk"
    (let [log (atom [])]
      (swap! log conj :before)
      (with-open [_ (closeable-map {:top-level-fn ^::system/fn (fn [] (swap! log conj :top-level-fn/close))
                                    :nested {:nested-fn ^::system/fn (fn [] (swap! log conj :nested-fn/close))
                                             :deepest ^::system/ignore {:nested-fn ^::system/fn (fn [] (swap! log conj :ignored/close))
                                                                        ::system/on-close (fn [_] (swap! log conj :ignored/nested-close))}
                                             ::system/on-close (fn [_] (swap! log conj :closeable-map/nested-close))}
                                    ::system/on-close (fn [_] (swap! log conj :closeable-map/close))})]
        (swap! log conj :with-open))
      (swap! log conj :after)
      (is (= @log
             [:before
              :with-open
              :closeable-map/close
              :top-level-fn/close
              :closeable-map/nested-close
              :nested-fn/close
              :after])))
    (let [log (atom [])]
      (swap! log conj :before)
      (with-open [_ (closeable-map {:nested {:leaf {:closeable/first (reify Closeable (close [_] (swap! log conj :on-close/closeable-first)))
                                                    :down {:deepest {:closeable (reify Closeable (close [_] (swap! log conj :on-close/deepest-1)))}
                                                           :auto-closeable (reify AutoCloseable (close [_] (swap! log conj :on-close/deepest-2)))
                                                           ::system/on-close (fn [_] (swap! log conj :on-close/nested-explicit-close))}
                                                    :ignored-key ^::system/ignore {:deepest {:closeable (reify Closeable (close [_] (swap! log conj :on-close/ignored)))}
                                                                                   :auto-closeable (reify AutoCloseable (close [_] (swap! log conj :on-close/ignored)))
                                                                                   ::system/on-close (fn [_] (swap! log conj :on-close/ignored))}
                                                    :closeable (reify Closeable (close [_] (swap! log conj :on-close/closeable)))}}
                                    ::system/on-close (fn [_] (swap! log conj :on-close/explicit-close))
                                    :other :cheddar})]
        (swap! log conj :with-open))
      (swap! log conj :after)
      (is (= @log
             [:before
              :with-open
              :on-close/explicit-close
              :on-close/closeable-first
              :on-close/nested-explicit-close
              :on-close/deepest-1
              :on-close/deepest-2
              :on-close/closeable
              :after])))))
