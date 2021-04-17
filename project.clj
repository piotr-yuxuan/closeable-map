(defproject piotr-yuxuan/closeable-map (-> "./resources/closeable-map.version" slurp .trim)
  :description "A Clojure map which implements java.io.Closeable"
  :url "https://github.com/piotr-yuxuan/closeable-map"
  :license {:name "European Union Public License 1.2 or later"
            :url "https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12"
            :distribution :repo}
  :scm {:name "git"
        :url "https://github.com/piotr-yuxuan/closeable-map"}
  :pom-addition [:developers [:developer
                              [:name "胡雨軒 Петр"]
                              [:url "https://github.com/piotr-yuxuan"]]]
  :global-vars {*warn-on-reflection* true}
  :plugins [[lein-tools-deps "0.4.5"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:project]})
