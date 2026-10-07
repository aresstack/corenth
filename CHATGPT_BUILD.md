# ChatGPT build support

This repository contains two build entry points for constrained execution environments.

## Smoke compile without Gradle

Run:

```bash
bash chatgpt-build.sh
```

The default mode compiles dependency-free Java 8 production files with `javac`. It avoids a Gradle Wrapper download and is intended for fast structural checks in environments without network access.

The script writes generated files below `build/chatgpt/` and lists skipped sources in `build/chatgpt/skipped-external-sources.txt`. A source is skipped when it imports an external library (Lucene, JUnit, OpenNLP, JSoup, PDFBox, POI and the platform bindings) or, transitively, a class of another skipped source, such as the composition root.

## Full Gradle build with a prepared cache

A full Gradle build still needs the Gradle distribution and external Maven dependencies. Prepare them once on a machine with network access, then provide the prepared `.chatgpt/gradle-home` directory together with the repository.

Run full mode with:

```bash
CHATGPT_FULL_GRADLE_BUILD=true bash chatgpt-build.sh
```

The full mode runs Gradle offline with `clean build`.

## Headless Swing test environment

The Exedra suite is designed to run on a headless Linux system. Lightweight Swing tests remain active; only the two display-dependent tests use existing JUnit `Assume` guards and are expected to be skipped when `GraphicsEnvironment.isHeadless()` is true.

A normal headless run of the Exedra module alone (`./gradlew :proasteion:exedra:test`) should therefore report approximately:

```text
48 passed, 2 skipped, 0 failed
```

The full `clean build` across all modules reports a larger total (394 tests on the Session 0 branch, 0 failures) with the same 2 skips, which are these Exedra tests.

Minimal container images still need working AWT font support. On Debian/Ubuntu-derived systems, install or provide at least:

```text
fontconfig
libfreetype6
fonts-dejavu-core
```

Useful diagnostics:

```bash
java -XshowSettings:properties -version 2>&1 | grep -E 'java.home|java.awt.headless'
ldconfig -p | grep -E 'fontconfig|freetype'
fc-list | head
./gradlew --offline :proasteion:exedra:test --stacktrace
```

Do not add blanket headless guards to lightweight Swing tests. Components such as `JPanel`, `JLabel`, `JTabbedPane` and `KeyStroke` do not require a display and should continue to provide coverage in headless CI. Do not force `java.awt.headless=false` or require Xvfb merely for the two intentionally display-dependent tests.

See `docs/analysis/exedra-headless-test-verification.md` for the verification details and limitations.

## Java 8 compatibility

Gradle may run on a newer JDK, but the project is compiled for Java 8. The root `build.gradle` sets `sourceCompatibility`, `targetCompatibility` and, on Java 9 or newer, `options.release = 8`.
