# Build- und Test-CI — Lücke und Schutzumfang

**Stand:** 2026-07-19

## Befund

Bis zur Einführung von `.github/workflows/build.yml` besaß Corenth keinen Workflow für Pull-Request-Builds oder automatische Tests. Der vorhandene Workflow `.github/workflows/chatgpt-compatible-release.yml` lief ausschließlich nach Push auf `main` und diente dem Erzeugen und Veröffentlichen des ChatGPT-kompatiblen ZIP-Artefakts.

Damit wurden insbesondere folgende Schutzmechanismen bei Pull Requests nicht automatisch ausgeführt:

- alle Modul-Unit- und Integrationstests,
- `architecture-tests` mit den ArchUnit-Regeln,
- Mediated-Access-Pflicht,
- Secret-Containment,
- innere/äußere Abhängigkeitsrichtungen,
- Exedra-Headless-Verhalten.

## Eingeführter Workflow

`.github/workflows/build.yml` läuft bei:

- jedem Pull Request,
- jedem Push auf `main`,
- manueller Ausführung über `workflow_dispatch`.

Der Workflow verwendet Temurin JDK 21, kompiliert das Projekt weiterhin mit Java-8-Zielvorgabe und führt aus:

```bash
./gradlew --no-daemon clean build --stacktrace
```

Da `architecture-tests` in `settings.gradle` enthalten ist, ist das ArchUnit-Regressionsnetz Bestandteil dieses Builds.

## Headless-Verhalten

Der Workflow verwendet keinen virtuellen X-Server. Er installiert nur die für AWT-Fontinitialisierung benötigten Pakete:

- `fontconfig`
- `libfreetype6`
- `fonts-dejavu-core`

Leichtgewichtige Swing-Tests bleiben aktiv. Displaypflichtige Tests dürfen ausschließlich über ihre vorhandenen gezielten `Assume`-Guards übersprungen werden.

Nach dem Gradle-Lauf werden JUnit-XML-Ergebnisse zusammengefasst. Übersprungene Tests werden namentlich in der GitHub-Actions-Zusammenfassung ausgewiesen. Bei einem Fehlschlag werden die Testberichte als kurzlebiges Workflow-Artefakt hochgeladen.

## Noch organisatorisch zu erledigen

Nach dem ersten grünen Lauf sollte der Check `Build and test (Java 8 target)` in den Branch-Regeln für `main` als verpflichtender Statuscheck hinterlegt werden. Erst diese Repository-Einstellung verhindert technisch, dass Pull Requests mit fehlgeschlagenem oder nicht ausgeführtem Build gemergt werden.

## Abgrenzung zum Release-Workflow

Der Release-Workflow bleibt separat bestehen. Er darf weiterhin Xvfb verwenden, weil sein Zweck ein reproduzierbares Offline-Paket und nicht die Validierung des echten headless CI-Verhaltens ist. Der neue Build-/Testworkflow ist das maßgebliche Regressionsnetz für Pull Requests und `main`.
