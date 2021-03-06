(defproject piotr-yuxuan/closeable-map (-> "./resources/closeable-map.version" slurp .trim)
  :description "A Clojure map which implements java.io.Closeable"
  :url "https://github.com/piotr-yuxuan/closeable-map"
  :license {:name "GNU General Public License v3.0 or later"
            :url "https://www.gnu.org/licenses/gpl-3.0.en.html"
            :distribution :repo
            :comments "See also GPL_ADDITION.org"}
  :scm {:name "git"
        :url "https://github.com/piotr-yuxuan/closeable-map.git"}
  :pom-addition [:developers [:developer
                              [:name "胡雨軒 Петр"]
                              [:url "https://github.com/piotr-yuxuan"]]]
  :global-vars {*warn-on-reflection* true}
  :plugins [[lein-tools-deps "0.4.5"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:project]})
