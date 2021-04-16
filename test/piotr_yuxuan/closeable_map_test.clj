(ns piotr-yuxuan.closeable-map-test
  (:require [clojure.test :refer [deftest testing is]]
            [piotr-yuxuan.closeable-map :as closeable-map])
  (:import (java.io Closeable)))

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
      (is (= (closeable-map/visitor m)
             (assoc m :nested-map nil)))
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
      (is (= (closeable-map/visitor m)
             (assoc-in m [:nested-map :closeable] nil)))
      (is (= @log
             [::before-close
              ::closeable
              ::before-nested-close
              ::after-nested-close
              ::nested-vector
              ::after-close]))))

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
                   ::after-nested-close]))))

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
             :closeable (reify Closeable (close [_] (swap! log conj ::closeable)))
             :nested-map ^::closeable-map/swallow {:closeable (reify Closeable (close [_]
                                                                                 (swap! log conj ::nested-map)
                                                                                 (throw (ex-info "nested-map" {}))))}
             :nested-vector ^::closeable-map/swallow [(reify Closeable (close [_]
                                                                         (swap! log conj ::nested-vector)
                                                                         (throw (ex-info "nested-vector" {}))))]
             ::closeable-map/after-close (fn [_] (swap! log conj ::after-close))}]
      (is (= m (closeable-map/visitor m)))
      (is (=  [::before-close
                   ::closeable
                   ::nested-map
                   ::nested-vector
                   ::after-close]))))

  (testing "fn"
    (let [log (atom nil)]
      (closeable-map/visitor
        {:closeable ^::closeable-map/fn (fn [] (reset! log ::reset!))})
      (is (= @log ::reset!)))))
