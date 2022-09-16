(ns piotr-yuxuan.closeable-map-test
  (:require [piotr-yuxuan.closeable-map :as closeable-map]
            [clojure.test :refer [deftest testing is]])
  (:import (java.io Closeable)
           (clojure.lang ExceptionInfo)))

(deftest visitor-test
  (testing "return value when no ignore"
    (let [m {:a :b}]
      (is (= m (closeable-map/visitor m))))
    (let [m {::closeable-map/before-close (fn [_])
             :closeable (reify Closeable (close [_]))
             :nested-map {:closeable (reify Closeable (close [_]))}
             :nested-vector [(reify Closeable (close [_]))]
             ::closeable-map/after-close (fn [_])}]
      (is (= m (closeable-map/visitor m)))))

  (testing "ignore"
    (let [log (atom ::untouched)]
      (is nil? (closeable-map/visitor
                 ^::closeable-map/ignore
                 {::closeable-map/before-close (fn [_] (reset! log ::before-close))
                  :closeable (reify Closeable (close [_] (reset! log ::closeable)))
                  :nested-map {:closeable (reify Closeable (close [_] (reset! log ::nested-map)))}
                  :nested-vector [(reify Closeable (close [_] (reset! log ::nested-vector)))]
                  ::closeable-map/after-close (fn [_] (reset! log ::after-close))}))
      (is (= @log ::untouched)))
    (let [log (atom [])
          m {::closeable-map/before-close (fn [_] (swap! log conj ::before-close))
             :closeable (reify Closeable (close [_] (swap! log conj ::closeable)))
             :nested-map ^::closeable-map/ignore {::closeable-map/before-close (fn [_] (swap! log conj ::before-nested-close))
                                                  :closeable (reify Closeable (close [_] (swap! log conj ::nested-closeable)))
                                                  ::closeable-map/after-close (fn [_] (swap! log conj ::after-nested-close))}
             :nested-vector [(reify Closeable (close [_] (swap! log conj ::nested-vector)))]
             ::closeable-map/after-close (fn [_] (swap! log conj ::after-close))}]
      (is (= m (closeable-map/visitor m)))
      (is (= @log
             [::before-close
              ::closeable
              ::nested-vector
              ::after-close])))
    (let [log (atom [])
          m {::closeable-map/before-close (fn [_] (swap! log conj ::before-close))
             :closeable (reify Closeable (close [_] (swap! log conj ::closeable)))
             :nested-map {::closeable-map/before-close (fn [_] (swap! log conj ::before-nested-close))
                          :closeable ^::closeable-map/ignore (reify Closeable (close [_] (swap! log conj ::nested-closeable)))
                          ::closeable-map/after-close (fn [_] (swap! log conj ::after-nested-close))}
             :nested-vector [(reify Closeable (close [_] (swap! log conj ::nested-vector)))]
             ::closeable-map/after-close (fn [_] (swap! log conj ::after-close))}]
      (is (= m (closeable-map/visitor m)))
      (is (= @log
             [::before-close
              ::closeable
              ::before-nested-close
              ::after-nested-close
              ::nested-vector
              ::after-close]))))

  (testing "nested ignore"
    (let [log (atom [])
          m {:a ^::closeable-map/ignore {:b-swallowed ^::closeable-map/fn #(swap! log conj ::ignored)
                                         :b (vary-meta
                                              {:c ^::closeable-map/fn #(swap! log conj ::closeable)}
                                              assoc ::closeable-map/ignore false)
                                         :b-shadowed ^::closeable-map/fn #(swap! log conj ::ignored)}}]
      (try (closeable-map/visitor m)
           (catch ExceptionInfo ex
             (swap! log conj (ex-message ex))))
      (is (= @log [::closeable]))))

  (testing "before / after"
    (let [log (atom [])]
      (closeable-map/visitor
        {::closeable-map/before-close (fn [_] (swap! log conj ::before-close))
         :closeable (reify Closeable (close [_] (swap! log conj ::closeable)))
         :nested-map {::closeable-map/before-close (fn [_] (swap! log conj ::before-nested-close))
                      :closeable (reify Closeable (close [_] (swap! log conj ::nested-closeable)))
                      ::closeable-map/after-close (fn [_] (swap! log conj ::after-nested-close))}
         :nested-vector [(reify Closeable (close [_] (swap! log conj ::nested-vector)))]
         ::closeable-map/after-close (fn [_] (swap! log conj ::after-close))})
      (is (= @log [::before-close
                   ::closeable
                   ::before-nested-close
                   ::nested-closeable
                   ::after-nested-close
                   ::nested-vector
                   ::after-close])))
    (let [log (atom [])]
      (closeable-map/visitor
        {:closeable (reify Closeable (close [_] (swap! log conj ::closeable)))
         :nested-vector (with-meta [(reify Closeable (close [_] (swap! log conj ::nested-vector)))]
                                   {::closeable-map/before-close (fn [_] (swap! log conj ::before-nested-close))
                                    ::closeable-map/after-close (fn [_] (swap! log conj ::after-nested-close))})})
      (is (= @log [::closeable
                   ::before-nested-close
                   ::nested-vector
                   ::after-nested-close])))
    (let [log (atom {})
          m {::closeable-map/before-close (fn [m] (swap! log assoc ::before-close m))
             ::closeable-map/after-close (fn [m] (swap! log assoc ::after-close m))}]
      (closeable-map/visitor m)
      (is (= @log {::before-close m
                   ::after-close m}))))

  (testing "swallow"
    (let [log (atom [])
          m ^::closeable-map/swallow {::closeable-map/before-close (fn [_]
                                                                     (swap! log conj ::before-close)
                                                                     (throw (ex-info "before-close" {})))
                                      :closeable (reify Closeable (close [_]
                                                                    (swap! log conj ::closeable)
                                                                    (throw (ex-info "closeable" {}))))
                                      :nested-map {:closeable (reify Closeable (close [_]
                                                                                 (swap! log conj ::nested-map)
                                                                                 (throw (ex-info "nested-map" {}))))}
                                      :nested-vector [(reify Closeable (close [_]
                                                                         (swap! log conj ::nested-vector)
                                                                         (throw (ex-info "nested-vector" {}))))]
                                      ::closeable-map/after-close (fn [_]
                                                                    (swap! log conj ::after-close)
                                                                    (throw (ex-info "after-close" {})))}]
      (is (= m (closeable-map/visitor m)))
      (is (= @log [::before-close
                   ::closeable
                   ::nested-map
                   ::nested-vector
                   ::after-close])))
    (let [log (atom [])
          m {::closeable-map/before-close (fn [_] (swap! log conj ::before-close))
             :closeable ^::closeable-map/swallow (reify Closeable (close [_]
                                                                    (swap! log conj ::closeable)
                                                                    (throw (ex-info "closeable" {}))))
             :nested-map ^::closeable-map/swallow {:closeable (reify Closeable (close [_]
                                                                                 (swap! log conj ::nested-map)
                                                                                 (throw (ex-info "nested-map" {}))))}
             :nested-vector ^::closeable-map/swallow [(reify Closeable (close [_]
                                                                         (swap! log conj ::nested-vector)
                                                                         (throw (ex-info "nested-vector" {}))))]
             ::closeable-map/after-close (fn [_] (swap! log conj ::after-close))}]
      (is (= m (closeable-map/visitor m)))
      (is (= @log [::before-close
                   ::closeable
                   ::nested-map
                   ::nested-vector
                   ::after-close]))
      @log)
    (let [log (atom [])
          m {::closeable-map/before-close ^::closeable-map/swallow (fn [_]
                                                                     (swap! log conj ::before-close)
                                                                     (throw (ex-info "before-close" {})))

             :closeable ^::closeable-map/swallow (reify Closeable (close [_]
                                                                    (swap! log conj ::closeable)
                                                                    (throw (ex-info "closeable" {}))))

             ::closeable-map/after-close ^::closeable-map/swallow (fn [_]
                                                                    (swap! log conj ::after-close)
                                                                    (throw (ex-info "after-close" {})))}]
      (is (= m (closeable-map/visitor m)))
      (is (= @log
             [::before-close
              ::closeable
              ::after-close]))))

  (testing "nested shallow"
    (let [log (atom [])
          m {:a ^::closeable-map/swallow {:b-swallowed ^::closeable-map/fn (fn []
                                                                             (swap! log conj ::b-swallowed)
                                                                             (throw (ex-info "b-swallowed" {})))
                                          :b (vary-meta
                                               {:c ^::closeable-map/fn (fn []
                                                                         (swap! log conj ::c-closeable)
                                                                         (throw (ex-info "c-exception" {})))}
                                               assoc ::closeable-map/swallow false)
                                          :b-shadowed ^::closeable-map/fn (fn []
                                                                            (swap! log conj ::fail-shadowed)
                                                                            (throw (ex-info "fail-shadowed" {})))}}]
      (try (closeable-map/visitor m)
           (catch ExceptionInfo ex
             (swap! log conj (ex-message ex))))
      (is (= @log [::b-swallowed ::c-closeable "c-exception"]))))

  (testing "nested ex-handler"
    (let [log (atom [])
          m ^::closeable-map/swallow {::closeable-map/ex-handler (fn [ex] (swap! log conj (ex-message ex)))
                                      :a {:b-swallowed ^::closeable-map/fn (fn []
                                                                             (swap! log conj ::b-swallowed)
                                                                             (throw (ex-info "b-swallowed" {})))
                                          :b {:c ^::closeable-map/fn (fn []
                                                                       (swap! log conj ::c-closeable)
                                                                       (throw (ex-info "c-exception" {})))}
                                          :b-shadowed ^::closeable-map/fn (fn []
                                                                            (swap! log conj ::fail-shadowed)
                                                                            (throw (ex-info "fail-shadowed" {})))}}]
      (closeable-map/visitor m)
      (is (= @log [::b-swallowed
                   "b-swallowed"
                   ::c-closeable
                   "c-exception"
                   ::fail-shadowed
                   "fail-shadowed"]))))

  (testing "fn"
    (let [log (atom nil)]
      (closeable-map/visitor
        {:closeable ^::closeable-map/fn (fn [] (reset! log ::reset!))})
      (is (= @log ::reset!)))))

(deftest tag-test
  (is (= (meta (let [a {}] (closeable-map/with-tag ::closeable-map/fn a)))
         #::closeable-map{:fn true}))
  (is (= (meta (let [a {}] (closeable-map/with-tag ::closeable-map/ignore a)))
         #::closeable-map{:ignore true}))
  (is (= (meta (let [a {}] (closeable-map/with-tag ::closeable-map/swallow a)))
         #::closeable-map{:swallow true})))

(deftest close-with-test
  (let [log (atom nil)]
    (closeable-map/close! (closeable-map/close-with
                            (memfn ^Closeable .close)
                            (reify Closeable
                              (close [_]
                                (swap! log conj ::close!)))))
    (is (= [::close!] @log)))
  (let [log (atom nil)]
    (closeable-map/close! (closeable-map/close-with
                            (fn [_] (swap! log conj ::close!))
                            {:any :clojure-object}))
    (is (= [::close!] @log))))

(deftest try-with-closeable-test
  (let [log (atom nil)]
    ;; Keyword are not Clojure objects, arrays are.
    (is (= (closeable-map/with-closeable* [{:keys [first-key]} (closeable-map/close-with
                                                                    (fn [_] (swap! log conj :a/close!))
                                                                    {:first-key ::a})
                                              [{:keys [second-key]}] (closeable-map/close-with
                                                                       (fn [_] (swap! log conj :b/close!))
                                                                       [{:second-key ::b}])
                                              [third-key] (closeable-map/close-with
                                                            (fn [_] (swap! log conj :c/close!))
                                                            [::c])]
                                          [first-key second-key third-key])
           [::a ::b ::c]))
    (is (not @log)))
  (let [log (atom nil)]
    (is (= (closeable-map/with-closeable* [a (reify Closeable (close [_] (swap! log conj ::a)))
                                              b (reify Closeable (close [_] (swap! log conj ::b)))
                                              c (reify Closeable (close [_] (swap! log conj ::c)))]
                                          :anything)
           :anything))
    (is (not @log)))
  (let [log (atom [])
        expected-ex (ex-info "expected" {})]
    (is (= ::caught-return
           (try
             (closeable-map/with-closeable* [a (reify Closeable (close [_] (swap! log conj ::a)))
                                                b (reify Closeable (close [_] (swap! log conj ::b)))
                                                c (reify Closeable (close [_] (swap! log conj ::c)))]
                                            (throw expected-ex))
             (catch ExceptionInfo actual-ex
               (swap! log conj actual-ex)
               ::caught-return))))
    (is (= [::c ::b ::a expected-ex] @log)))
  (let [log (atom [])
        expected-ex (ex-info "expected" {})]
    (is (= ::caught-return
           (try
             (closeable-map/with-closeable* [a (reify Closeable (close [_] (swap! log conj ::a)))
                                                b (throw expected-ex)
                                                c (reify Closeable (close [_] (swap! log conj ::c)))]
                                            ::body)
             (catch ExceptionInfo actual-ex
               (swap! log conj actual-ex)
               ::caught-return))))
    (is (= [::a expected-ex] @log))))

(deftest closeable-map*-test
  (testing "closeable-map*"
    (testing "no definition exception, closing"
      (let [log (atom [])
            a ^::closeable-map/fn #(swap! log conj ::a)
            b ^::closeable-map/fn #(swap! log conj ::b)
            c ^::closeable-map/fn #(swap! log conj ::c)
            m (closeable-map/closeable-map*
                {:a a
                 :nested {:b b
                          :c c
                          :d ::d}})]
        (is (= m {:a a
                  :nested {:b b
                           :c c
                           :d ::d}}))
        (.close ^Closeable m)
        (is (= [::a ::b ::c] @log))))
    (testing "when an exception happens while evaluating a closeable*, closing all the previous closeable* in order"
      (let [log (atom [])
            expected-ex (ex-info "expected" {})]
        (try (closeable-map/closeable-map*
               {:a (closeable-map/closeable* ^::closeable-map/fn #(swap! log conj ::a))
                :nested {:b (closeable-map/closeable* ^::closeable-map/fn #(swap! log conj ::b))
                         :c (closeable-map/closeable* ^::closeable-map/fn #(swap! log conj ::c))
                         :d (throw expected-ex)
                         :e (closeable-map/closeable* ^::closeable-map/fn #(swap! log conj ::e))}})
             (catch ExceptionInfo actual-ex
               (swap! log conj actual-ex)
               ::caught-return))
        (is (= [::c ::b ::a expected-ex] @log))))
    (testing "no definition exception, closing in order each closeable* only once"
      (let [log (atom [])
            ^Closeable c (closeable-map/closeable-map*
                           {:a (closeable-map/closeable* ^::closeable-map/fn #(swap! log conj ::a))
                            :nested {:b (closeable-map/closeable* ^::closeable-map/fn #(swap! log conj ::b))
                                     :c (closeable-map/closeable* ^::closeable-map/fn #(swap! log conj ::c))
                                     :d (closeable-map/closeable* ^::closeable-map/fn #(swap! log conj ::d))}})]
        (.close c)
        (is (= [::d ::c ::b ::a] @log))))
    (testing "closeable* not reachable as a map value"
      (let [log (atom [])
            ^Closeable c (closeable-map/closeable-map*
                           (closeable-map/closeable* ^::closeable-map/fn #(swap! log conj ::a))
                           (closeable-map/closeable* ^::closeable-map/fn #(swap! log conj ::b))
                           {:some [(closeable-map/closeable* ^::closeable-map/fn #(swap! log conj ::c))
                                   (closeable-map/closeable* ^::closeable-map/fn #(swap! log conj ::d))]})]
        (.close c)
        (is (= [::d ::c ::b ::a] @log))))))
