# Development

## References

- Getting started: <https://clojure.org/guides/getting_started>
- `deps.edn` guide: <https://clojure.org/guides/deps_and_cli>
- `deps.edn` reference: <https://clojure.org/reference/deps_and_cli>
- Tools and how-to guides: <https://practicalli.github.io/clojure/>
- Leiningen manual: <https://github.com/technomancy/leiningen>

## Usage

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
lein do clean, jar
```

Install it locally:

``` zsh
lein install
```

Create a new version once a jar has been created:
- Make sure all reasonable documentation is here
- Update resources/closeable-map.version
- Create a commit with title `Version x.y.z`
- Create a git tag

``` zsh
lein do clean, test, jar
lein deploy clojars
```

Deploy it to GitHub packages with [this
guide](https://docs.github.com/en/packages/guides/configuring-apache-maven-for-use-with-github-packages)
and:

``` zsh
mvn deploy -DaltDeploymentRepository=github::default::https://maven.pkg.github.com/piotr-yuxuan/closeable-map
```

## Notes on `pom.xml`

If you don't plan to install/deploy the library, you can remove the
`pom.xml` file but you will also need to remove `:sync-pom true` from
the `deps.edn` file (in the `:exec-args` for `depstar`).

As of now it is suggested to run `lein pom` to update the pom before
installing a jar or deploying a new version, so that the file `pom.xml`
is correctly updated by Leiningen (especially the scm revision), which I
don't know yet how to do with `deps.edn` tooling.
