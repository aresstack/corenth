# ChatGPT build support

This repository contains two build entry points for constrained execution environments.

## Smoke compile without Gradle

Run:

```bash
bash chatgpt-build.sh
```

The default mode compiles dependency-free Java 8 production files with `javac`. It avoids a Gradle Wrapper download and is intended for fast structural checks in environments without network access.

The script writes generated files below `build/chatgpt/` and lists skipped sources in `build/chatgpt/skipped-external-sources.txt`.

## Full Gradle build with a prepared cache

A full Gradle build still needs the Gradle distribution and external Maven dependencies. Prepare them once on a machine with network access, then provide the prepared `.chatgpt/gradle-home` directory together with the repository.

Run full mode with:

```bash
CHATGPT_FULL_GRADLE_BUILD=true bash chatgpt-build.sh
```

The full mode runs Gradle offline with `clean build`.

## Java 8 compatibility

Gradle may run on a newer JDK, but the project is compiled for Java 8. The root `build.gradle` sets `sourceCompatibility`, `targetCompatibility` and, on Java 9 or newer, `options.release = 8`.
