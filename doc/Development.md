# Development

## References

- Getting started: <https://clojure.org/guides/getting_started>
- `deps.edn` guide: <https://clojure.org/guides/deps_and_cli>
- `deps.edn` reference: <https://clojure.org/reference/deps_and_cli>
- Tools and how-to guides: <https://practicalli.github.io/clojure/>
- Leiningen manual: <https://github.com/technomancy/leiningen>
- GitHub actions: <https://docs.github.com/en/actions>

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
lein do clean, install
```

## GitHub Actions

- Once a month, automatically tries to update dependencies and push a commit is tests pass.
- When a tag is pushed to main branch, run tests, build, and deploy package to Clojars.
- When a commit is pushed to any branch, run tests.

## Deploying a new version

Create a new version once a jar has been created:
- Make sure all reasonable documentation is here
- Update resources/closeable-map.version
- Create a commit with title `Version x.y.z`
- Create a git tag and push it; see GitHub action

Deploy it to GitHub packages with [this
guide](https://docs.github.com/en/packages/guides/configuring-apache-maven-for-use-with-github-packages)
and:

``` zsh
mvn deploy -DaltDeploymentRepository=github::default::https://maven.pkg.github.com/piotr-yuxuan/closeable-map
```
