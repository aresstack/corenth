# TODO — Ersten Build-/Test-CI-Lauf verifizieren

**Stand:** 2026-07-19

Der neue Workflow `.github/workflows/build.yml` wurde auf `main` angelegt. Noch zu verifizieren ist der erste reale GitHub-Actions-Lauf.

## Erwartungen

- Workflowname: `Build and test`
- Jobname: `Build and test (Java 8 target)`
- Trigger: Pull Request, Push auf `main` oder manuell
- Befehl: `./gradlew --no-daemon clean build --stacktrace`
- `architecture-tests` wird ausgeführt
- Exedra läuft headless ohne Xvfb
- zwei displaypflichtige Exedra-Tests werden gezielt übersprungen
- übersprungene Tests erscheinen in der Job-Zusammenfassung
- bei Fehlern werden Gradle-Testberichte hochgeladen

## Danach

Nach einem grünen Lauf den Check gemäß `docs/todo-ci-branch-protection.md` als verpflichtenden Statuscheck für `main` konfigurieren.
