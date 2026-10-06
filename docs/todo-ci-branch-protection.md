# TODO — CI-Statuscheck für `main` verpflichtend machen

**Stand:** 2026-10-06 (Session 0) · ursprünglich 2026-07-19

Der Workflow `.github/workflows/build.yml` führt den vollständigen Gradle-Build einschließlich `architecture-tests` bei Pull Requests und Pushes auf `main` aus. Er ist auf `main` grün (Läufe 6–8 vom 2026-07-19), aber noch nie bei einem Pull Request gelaufen.

**Verifizierter Ist-Zustand (GitHub-API, 2026-10-06):** Für `main` existiert nur das Ruleset „main“ mit den Regeln `deletion` und `non_fast_forward`. Klassische Branch Protection ist nicht aktiv (`protection.enabled = false`), `required_status_checks` ist leer. Der Check `Build and test (Java 8 target)` ist also **nicht** verpflichtend; Merges ohne grünen Build sind technisch möglich.

In den GitHub-Branch-Regeln für `main` ist daher noch einzustellen:

- Pull Requests vor dem Merge verlangen,
- Statuschecks verlangen,
- den Check **`Build and test (Java 8 target)`** als verpflichtend auswählen,
- veraltete Branches vor dem Merge aktualisieren lassen, sofern dies zum gewünschten Merge-Workflow passt,
- Administrator-Bypass nur bewusst zulassen.

## Verifikation

1. Einen Pull Request öffnen (PR #41 wird ohne Merge geschlossen, siehe `docs/analysis/ci-build-test-gap.md`; der nächste echte PR, z. B. der Session-0-Branch, liefert den Nachweis).
2. Prüfen, dass der Workflow **Build and test** auf das `pull_request`-Ereignis startet.
3. Prüfen, dass Unit-, Integrations- und ArchUnit-Tests ausgeführt werden.
4. Prüfen, dass übersprungene Tests in der Actions-Zusammenfassung sichtbar sind.
5. Den erfolgreichen Check anschließend in den Branch-Regeln als verpflichtend auswählen.

Diese Repository-Einstellung kann nicht durch eine normale Commit-Datei erzwungen werden und muss über GitHub Settings beziehungsweise Rulesets vorgenommen werden.
