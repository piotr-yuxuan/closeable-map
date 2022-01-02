(defproject piotr-yuxuan/closeable-map (-> "./resources/closeable-map.version" slurp .trim)
  :description "Application state management made simple: a Clojure map that implements java.io.Closeable."
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
  :aot :all
  :profiles {:github {:github/topics ["map" "clojure" "state-management" "component"
                                      "state" "mount" "integrant" "closeable" "deps-edn"
                                      "tools-cli" "with-open" "clojure-maps"]}
             :provided {:dependencies [[org.clojure/clojure "1.10.3"]]}
             :dev {:global-vars {*warn-on-reflection* true}}
             :jar {:jvm-opts ["-Dclojure.compiler.disable-locals-clearing=false"
                              "-Dclojure.compiler.direct-linking=true"]}
             :kaocha [:test {:dependencies [[lambdaisland/kaocha "1.60.977"]]}]}
  :deploy-repositories [["clojars" {:sign-releases false
                                    :url "https://clojars.org/repo"
                                    :username :env/WALTER_CLOJARS_USERNAME
                                    :password :env/WALTER_CLOJARS_PASSWORD}]
                        ["github" {:sign-releases false
                                   :url "https://maven.pkg.github.com/piotr-yuxuan/closeable-map"
                                   :username :env/GITHUB_ACTOR
                                   :password :env/WALTER_GITHUB_PASSWORD}]])
