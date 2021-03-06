A Clojure map which implements `java.io.Closeable`.

# Installation

[![](https://img.shields.io/clojars/v/piotr-yuxuan/closeable-map.svg)](https://clojars.org/piotr-yuxuan/closeable-map)
[![cljdoc badge](https://cljdoc.org/badge/piotr-yuxuan/closeable-map)](https://cljdoc.org/d/piotr-yuxuan/closeable-map/CURRENT)
[![GitHub license](https://img.shields.io/github/license/piotr-yuxuan/closeable-map)](https://github.com/piotr-yuxuan/closeable-map/blob/main/LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/piotr-yuxuan/closeable-map)](https://github.com/piotr-yuxuan/closeable-map/issues)

# TL;DR example

``` clojure
;; in your project
(defn start
  "Return an running context with stateful references which can be closed."
  [config]
  (assoc config
    :server (http/start-server (api context) {:port 3030})
    :producer (kafka-producer config)
    :consumer (kafka-consumer config)))

;; in test file
(with-open [context (piotr-yuxuan/closeable-map (start config))]
  (testing "unit test with isolated, repeatable context"
    (is (= :yay/🚀 (some-business/function context)))))
```

# Description

You might not need `closeable-map`, perhaps
[jarohen/with-open](https://github.com/jarohen/with-open) or
[robertluo/fun-map](https://github.com/robertluo/fun-map) are better fit
for you. They provide more general, more powerful tools. This library
focus on doing one thing: a map which represents a execution context,
which you can close.

Some Clojure datastructures implement `IFn`:

``` clojure
({:a 1} :a) ;; => 1
(remove #{:a} [:a :b :c]) ;; => '(:b :c)
([:a :b :c] 1) ;; => :b
```

Clojure maps (`IPersistentMap`) implement `IFn`, for `invoke()` of one
argument (a key) with an optional second argument (a default value),
i.e. maps are functions of their keys. `nil` keys and values are fine.

I have found desirable in some cases to put stateful references in a
`context` map:

``` clojure
;; Start an API
(defn -main
  []
  (let [config (config/load-config)
        context {:producer (kafka-producer config)
                 :consumer (kafka-consumer config)}]
    (http/start-server
      (api context)
      {:port 3030})))
```

So far so good, but what about testing? It would be nice to have tests
like:

``` clojure
;; doesn't work
(with-open [context {:producer (kafka-producer config)
                     :consumer (kafka-consumer config)}]
  (is (= :yay/🚀 (some-business/function context))))
```

so that a test context is declared, assumptions are checked against it,
and finally context is closed.

This library defines a new type of map, `CloseableMap`, which implements
`java.io.Closeable`. It provides one function to create such map from a
Clojure map. When key `:close` is present, it is assumed that it is a
function which knows how to destroy the state, or a collection of
functions, of which each destroys of part of the state.

``` clojure
(with-open [context (closeable-map {:producer (kafka-producer config)
                                    :consumer (kafka-consumer config)
                                    :close [(fn [{:keys [producer]}] (.close producer))
                                            (fn [{:keys [consumer]}] (.close consumer))]})]
  (is (= :yay/🚀 (some-business/function context))))
```

# References

-   Getting started: <https://clojure.org/guides/getting_started>
-   `deps.edn` guide: <https://clojure.org/guides/deps_and_cli>
-   `deps.edn` reference: <https://clojure.org/reference/deps_and_cli>
-   Tools and how-to guides: <https://practicalli.github.io/clojure/>
-   Leiningen manual: <https://github.com/technomancy/leiningen>

# Usage

Invoking the function provided by this library from the command-line. It
returns an unimpressive map, which is what we expect:

``` zsh
clojure -X:run-x :arg '{:a 1}'
{:a 1}
```

Also, see
[./test/piotr~yuxuan~/closeable~maptest~.clj](./test/piotr_yuxuan/closeable_map_test.clj).

This project was created with:

``` zsh
clojure -X:project/new :name piotr-yuxuan/closeable-map
```

Run the project's tests:

``` zsh
clojure -M:test:runner
```

Lint your code with:

``` zsh
clojure -M:lint/idiom
clojure -M:lint/kondo
```

Visualise links between project vars with:

``` zsh
mkdir graphs
clojure -M:graph/vars-svg
```

Build a deployable jar of this library:

``` zsh
lein pom
clojure -X:jar
```

This will update the generated `pom.xml` file to keep the dependencies
synchronized with your `deps.edn` file.

Install it locally:

``` zsh
lein pom
clojure -X:install
```

Create a new version once a jar has been created:
- Make sure all reasonable documentation is here
- Update resources/closeable-map.version
- `lein pom`
- Create a commit with title `Version x.y.z`
- Create a git tag

Deploy it to Clojars -- needs `CLOJARS_USERNAME` and `CLOJARS_PASSWORD`
environment variables (requires the `pom.xml` file):

``` zsh
lein pom
clojure -X:deploy
```

Deploy it to GitHub packages with [this
guide](https://docs.github.com/en/packages/guides/configuring-apache-maven-for-use-with-github-packages)
and:

``` zsh
mvn deploy -DaltDeploymentRepository=github::default::https://maven.pkg.github.com/piotr-yuxuan/closeable-map
```

# Notes on `pom.xml`

If you don't plan to install/deploy the library, you can remove the
`pom.xml` file but you will also need to remove `:sync-pom true` from
the `deps.edn` file (in the `:exec-args` for `depstar`).

As of now it is suggested to run `lein pom` to update the pom before
installing a jar or deploying a new version, so that the file `pom.xml`
is correctly updated by Leiningen (especially the scm revision), which I
don't know yet how to do with `deps.edn` tooling.
