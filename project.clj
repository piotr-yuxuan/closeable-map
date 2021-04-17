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
  :dependencies [[potemkin/potemkin "0.4.5"]]
  :global-vars {*warn-on-reflection* true}
  :aot :all
  :profiles {:dev {:jvm-opts ["-Dclojure.compiler.disable-locals-clearing=true"]}
             :jar {:jvm-opts ["-Dclojure.compiler.disable-locals-clearing=false"
                              "-Dclojure.compiler.direct-linking=true"]}
             :provided {:dependencies [[org.clojure/clojure "1.10.2"]]}
             :tool {:global-vars {*warn-on-reflection* false} ; we don't care reflection in tooling code
                    :plugins [[lein-nomis-ns-graph "0.14.6"] ; must stay the first, see https://github.com/simon-katz/lein-nomis-ns-graph#troubleshooting
                              [jonase/eastwood "0.3.14"]
                              [lein-bikeshed "0.5.2"]
                              [lein-cloverage "1.2.2"]
                              [lein-kibit "0.1.8"]
                              [lein-licenses "0.2.2"]
                              [lein-nvd "1.4.1"]
                              [ns-sort "1.0.0"]
                              [mutant "0.2.0"] ; source: https://github.com/pithyless/mutant
                              [venantius/yagni "0.1.7"]]}
             :kaocha [:test {:dependencies [[lambdaisland/kaocha "1.0.732"]]}]}
  :deploy-repositories [["clojars" {:sign-releases false
                                    :url "https://clojars.org/repo"
                                    :username :env/CLOJARS_USERNAME
                                    :password :env/CLOJARS_TOKEN}]]
  :plugins [[lein-ancient "0.7.1-SNAPSHOT"]] ; should be within tool, but don't accept it
  :aliases {"file-lint" ["with-profile" "tool" "bikeshed"] ; long lines, EOF, docstrings…
            "kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner" "--watch" "--fail-fast"] ; fast, random test runner
            "licenses" ["with-profile" "tool" "licenses" ":csv"] ; > './doc/licenses'
            "mutation-testing" ["with-profile" "tool" "trampoline" "mutate"]
            "simple-lint" ["with-profile" "tool" "eastwood" "{:namespaces [:source-paths]}"]
            ;; Better linter as command-line tool `clj-kondo --lint src test`.
            "static-analysis" ["with-profile" "tool" "kibit"] ; avoid code tautologies and so on
            "bump-dependency-versions" ["ancient" "upgrade" ":check-clojure" ":all"] ; bump dependency versions
            "test-coverage" ["with-profile" "tool" "cloverage"]
            "unused-code" ["with-profile" "tool" "yagni"]
            "ns-sort" ["with-profile" "tool" "ns-sort"]
            "vulnerabilities" ["with-profile" "tool" "nvd" "check"] ; > ./doc/KNOWN_VULNERABILITIES.log
            "viz-dependency-tree" ["with-profile" "tool" "do"
                                   "nomis-ns-graph" ":show-non-project-deps" "false" ":filename" "./doc/namespaces,"
                                   "nomis-ns-graph" ":show-non-project-deps" "true" ":filename" "./doc/dependencies"]})
