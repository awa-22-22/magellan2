# Building Magellan 2 with Maven

Besides the Ant build (`build.xml`, which also creates the installers), the
project can be built with Maven. The Maven build produces a single executable
**fat jar** that contains all compiled classes and all libraries from `lib/`.

## Requirements

- JDK 11 or newer (the build targets Java 11)
- Maven 3.6+
- On the first build, internet access to Maven Central is required (for the
  build plugins and the test dependencies).

## Building

```bash
mvn package
```

This compiles all sources, runs the unit tests, and creates both

```
target/magellan2-2.1.2.jar          # the fat jar alone
target/magellan2-2.1.2-dist.zip     # a runnable distribution (see below)
```

To skip the tests for a quick build:

```bash
mvn package -DskipTests
```

## Running

Magellan reads its configuration and data files (`etc/`, `help/`) from the
working directory at runtime — only the classpath is bundled into the jar.
The jar alone is therefore **not standalone**: running
`java -jar target/magellan2-2.1.2.jar` only works if an `etc/` directory
happens to be reachable from the current directory (e.g. the project root,
since `Resources.initialize` also checks one directory up), and otherwise
fails with `RuntimeException: Could NOT find location Magellan`.

For a copy that works from anywhere, unpack the distribution zip and run the
jar from inside it (or use the provided start script):

```bash
unzip target/magellan2-2.1.2-dist.zip -d /somewhere
cd /somewhere/magellan2-2.1.2
./magellan.sh          # or magellan.bat on Windows, or: java -jar magellan2-2.1.2.jar
```

Alternatively, point the jar at the project root explicitly with `-d`
(the directory must exist and be writable, or the option is silently
ignored):

```bash
java -jar target/magellan2-2.1.2.jar -d /path/to/magellan2
```

## How the build works

- **Source folders**: the Eclipse source roots `src-library`, `src-client`
  and `src-plugins` are registered as compile source roots, `src-test` as the
  test source root (via `build-helper-maven-plugin`). Compilation uses
  release 11 and ISO-8859-1 encoding, same as `build.xml`.
- **Dependencies**: all jars shipped in `lib/` are referenced with
  `system` scope. Several of them (the Swing skins, idw-gpl, jimi, macify,
  jfreechart, …) are not available on Maven Central, so the exact jars from
  the repository are used, mirroring `.classpath`.
- **Tests**: JUnit 4.13 and Hamcrest 1.3 (the same versions as in
  `lib/internal/`) are resolved from Maven Central instead. This is required
  because Surefire does not recognize system-scoped JUnit artifacts and would
  otherwise fall back to its JUnit 3 runner, which ignores `@BeforeClass`
  and `@Ignore` annotations and causes spurious test failures.
  `E3CommandParserTest` is excluded, exactly as in `build.xml`, and the tests
  run with `-Djava.awt.headless=true`.
- **Fat jar**: the maven-shade-plugin cannot merge system-scoped
  dependencies, so packaging is done with `maven-antrun-plugin` using an Ant
  `jar` task. The compiled classes plus the unpacked contents of all `lib`
  jars are merged into one jar; the first occurrence of a duplicate entry
  wins. The jars are merged in the classpath order of
  `etc/manifest.mf.template` (the order used by the classic Ant release), so
  the fat jar resolves the same class versions as the classic installation.
- **JGoodies Looks conflict**: `lib/skins/skinsCollected.jar` bundles a newer
  JGoodies Looks (2.x) on top of many other Look & Feels, while the shared
  `com.jgoodies.looks.*` classes come from `lib/skins/looks-1.3b1.jar` (first
  on the classpath, as in the classic release). The 2.x
  `com.jgoodies.looks.windows.WindowsMenuItemRenderer` cannot extend the
  final `MenuItemRenderer` of 1.3b1 and therefore fails to load with an
  `IncompatibleClassChangeError` (which the `RendererLoader` tripped over when
  scanning jars for map renderers). The class is unusable in this classpath
  layout, so it is excluded from the fat jar.
- **Signature stripping**: `lib/activation.jar` is a signed jar. Its
  signature files (`META-INF/*.SF`, `*.RSA`, `*.DSA`) would be invalid after
  merging and break class loading, so they are excluded from the fat jar.
- **Distribution zip**: `maven-assembly-plugin` runs a second execution
  bound to the `package` phase, after the antrun fat-jar build (so it picks
  up the finished jar). It uses the custom descriptor
  `src/assembly/dist.xml` to lay out a runnable copy: the fat jar, the
  runtime data Magellan reads from the working directory (`etc/*.properties`,
  `etc/*.ini`, `etc/images`, `etc/rules`, `etc/plugins` — the same set as
  Ant's `copy_release_data`), `doc/`, the top-level readme/license files, an
  empty `plugins/` folder for user plugins, and start scripts
  (`src/main/scripts/magellan.sh` / `.bat`, new for the Maven build — the
  Ant `installer/*.sh`/`.bat` scripts point at the install4j launcher and
  the classic four-jar layout, so they don't fit the Maven fat jar). Not
  included: the JavaHelp jar (`help/`, built by the separate
  `build_help`/`index_help` Ant targets, not yet migrated) and `etc/names`
  (used by the name generator but, like `help/`, not part of the classic
  `release/` layout produced by `copy_release_data` either).

## Relation to the Ant build (`build.xml`)

The Maven build currently covers the *compile → test → package* part of the
Ant build and nothing else. Both builds can coexist: Maven writes only to
`target/` and does not touch the Ant output directories (`classes/`,
`release/`, `macos/`). To replace Ant completely one day, the pieces listed
in the second table below still have to be migrated.

### Covered by Maven

| Ant target | Maven equivalent |
|---|---|
| `build_library`, `build_client`, `build_plugins` | `mvn compile` — one module instead of three separate compilation steps |
| `build_test` | `mvn test-compile` |
| `run_tests` | `mvn test` (Surefire; `E3CommandParserTest` excluded as well) |
| `build_library_jar`, `build_client_jar`, `build_plugins_jar` | `mvn package` — single fat jar instead of `magellan-library/client/plugins.jar` plus the `lib/` folder |
| `clear-all` | `mvn clean` (cleans only `target/`) |
| `copy_release_data` (partially), `zip_release` | `maven-assembly-plugin` with `src/assembly/dist.xml` — produces `target/magellan2-2.1.2-dist.zip` (fat jar, `etc/`, `doc/`, readme files, start scripts). Missing vs. Ant: `lib/` (not needed, contents are inside the fat jar), the JavaHelp jar, and the templated `etc/VERSION` |

### Not yet covered (needed for a complete Ant replacement)

| Ant target | What it does | Possible Maven approach |
|---|---|---|
| `increase_build_number`, `update_version`, `print_version` | increments `.build.number`, filters `etc/VERSION.template` into `etc/VERSION` and `VERSION` | `buildnumber-maven-plugin` + resource filtering; the version is currently fixed in the pom |
| `build_help`, `index_help` | runs the JavaHelp `Indexer` for `help/de` and `help/en`, packs `magellan-help.jar` | `maven-antrun-plugin` (`java` task) or `exec-maven-plugin`; also needs adding to the dist assembly |
| `build_jar_and_distribute` (IzPack part) | filters `installer/izpack-install.template.xml` and runs the IzPack `standalone-compiler.jar` | `maven-antrun-plugin` or `exec-maven-plugin` |
| `installer4j`, `distribute_install4j` | builds the install4j installers (requires the `INSTALL4J_KEY` environment variable) | `com.install4j:install4j-maven-plugin` |
| macOS bundle part of `build_jar_and_distribute` | assembles `macos/Magellan.app` | `maven-assembly-plugin` or antrun |
| `doc` | source jar + Javadoc (locale `de_DE`, ISO-8859-1, excludes `src-test` and `installer`) | `maven-source-plugin`, `maven-javadoc-plugin` |
| manifest templating (`etc/manifest.mf.template`) | versioned manifest with build number and user | `maven-jar-plugin` manifest entries |

### Intentional differences

- **One fat jar vs. four jars plus lib folder**: Ant ships
  `magellan-library.jar`, `magellan-client.jar`, `magellan-plugins.jar` and
  `magellan-help.jar` together with `lib/*.jar`; Maven merges everything into
  a single executable jar. If the classic layout is ever wanted back, the
  project would have to be split into Maven modules (`library`, `client`,
  `plugins`) — a much bigger refactoring.
- **Version handling**: Ant composes the version from `VERSION.MAJOR/MINOR/SUB`
  plus the build number at build time; the pom fixes it at `2.1.2`.
- **Test execution**: Ant runs `AFirstTest` separately before all other
  tests; Maven runs everything in one Surefire pass (`AFirstTest` only prints
  the working directory, so the ordering is irrelevant).

## Notes

- `mvn clean` removes the `target/` directory. `target/` is listed in
  `.gitignore`.
- The Maven build intentionally does not create installers (IzPack /
  install4j), Javadoc, or the source jar — use `build.xml` for those (see the
  table above). It does create a runnable distribution zip (see "Running"
  above), just not the JavaHelp jar bundled inside the Ant one.
