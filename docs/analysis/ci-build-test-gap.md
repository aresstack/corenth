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

## Stand 2026-10-06 (Session 0): Was bewiesen ist und was nicht

Verifiziert gegen die GitHub-API:

| Aspekt | Befund |
| --- | --- |
| Build-CI auf `main` | ✅ Workflow `Build and test` existiert; Läufe 6, 7 und 8 (Commits `2cd8b12`, `4c745e3`, `6eb39c3`, alle `push`, 2026-07-19) sind grün, Lauf 8 meldet 374 Tests, 0 Fehler, 2 Skips (beide Exedra), `architecture-tests` enthalten. Läufe 1–5 wurden nur durch die Concurrency-Gruppe abgebrochen, nicht durch Fehler. |
| PR-Ausführung | ❌ Noch nie bewiesen. Es gibt keinen einzigen `pull_request`-Lauf. PR #41 besitzt null Check-Runs, weil sein Stand (`d626cfc`, Basis `3116ed6`) vor der Einführung von `build.yml` liegt und seither nicht aktualisiert wurde. |
| Required Check / Branch Protection | ❌ Nicht konfiguriert. Die API meldet für `main` `protection.enabled = false` und `required_status_checks` leer (`enforcement_level: off`). `protected: true` stammt allein aus dem Ruleset „main“ (angelegt 2026-07-19 20:57 UTC) mit den Regeln `deletion` und `non_fast_forward`; weder „Require a pull request“ noch „Require status checks“ sind aktiv. |

Daraus folgt: Die CI ist ein vorhandenes, auf `main` grünes Regressionsnetz, aber **kein erzwungenes PR-Merge-Gate**. Dokumente, Issues oder Pläne dürfen sie nicht als solches darstellen, bis die beiden Punkte unten erledigt sind.

### Verbleibende administrative Aktion (nur über GitHub-Einstellungen möglich)

1. Den ersten PR gegen `main` öffnen oder aktualisieren und prüfen, dass der Workflow `Build and test` auf das `pull_request`-Ereignis anspringt und grün endet. Der Session-0-Branch (`claude/session-0-mediated-lifecycle`) ist dafür der natürliche erste Kandidat.
2. Im Ruleset „main“ (Settings → Rules → Rulesets) ergänzen: „Require a pull request before merging“ und „Require status checks to pass“ mit dem Check **`Build and test (Java 8 target)`**; optional „Require branches to be up to date before merging“. Administrator-Bypass nur bewusst zulassen.

Beides lässt sich nicht durch eine Datei im Repository erzwingen; Workarounds im Repository sind nicht vorgesehen.

### Entscheidung zu PR #41 und Issue #40

PR #41 fügt ausschließlich `tasks.withType(Test).configureEach { testLogging { events "skipped" } }` in `proasteion/exedra/build.gradle` ein, damit übersprungene Tests im Gradle-Konsolenlog erscheinen. Seit `build.yml` werden übersprungene Tests aus den JUnit-XML-Dateien **aller** Module namentlich in der Actions-Zusammenfassung ausgewiesen, also auf der in #40 geforderten „appropriate shared level“. Der PR ist damit für den CI-Zweck redundant und zudem auf Exedra beschränkt; er wird nicht gemergt und sollte ohne Merge geschlossen werden. Falls lokale Konsolen-Sichtbarkeit gewünscht ist, gehört die `testLogging`-Konfiguration in das Root-`build.gradle` für alle Subprojekte, nicht in ein einzelnes Modul; das ist bewusst nicht Teil von Session 0.

Issue #40: Die Aufgaben 1–3 sind durch Lauf 8 auf `main` erfüllt (Exedra-Suite grün, genau die zwei displaypflichtigen Tests übersprungen und sichtbar). Das Issue kann geschlossen werden, sobald der Eigentümer PR #41 ohne Merge geschlossen hat. Issue #45: Aufgaben 1–3 erfüllt; Aufgabe 4 (Required Check) und der PR-Nachweis bleiben offen. Aufgabe 5 („PR #41 erneut ausführen“) entfällt mit der Schließung von #41; der PR-Nachweis kommt vom nächsten echten PR.

## Abgrenzung zum Release-Workflow

Der Release-Workflow bleibt separat bestehen. Er darf weiterhin Xvfb verwenden, weil sein Zweck ein reproduzierbares Offline-Paket und nicht die Validierung des echten headless CI-Verhaltens ist. Der neue Build-/Testworkflow ist das maßgebliche Regressionsnetz für Pull Requests und `main`.
