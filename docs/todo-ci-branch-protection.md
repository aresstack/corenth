# TODO — CI-Statuscheck für `main` verpflichtend machen

**Stand:** 2026-10-06 (Session 0) · ursprünglich 2026-07-19

Der Workflow `.github/workflows/build.yml` führt den vollständigen Gradle-Build einschließlich `architecture-tests` bei Pull Requests und Pushes auf `main` aus. Er ist auf `main` grün (Läufe 6–8 vom 2026-07-19) und seit PR #46 auch bei einem Pull Request nachgewiesen (Lauf 9 vom 2026-10-06: 394 Tests, 0 Fehler, 2 Skips).

**Verifizierter Ist-Zustand (GitHub-API, 2026-10-06):** Für `main` existiert nur das Ruleset „main“ mit den Regeln `deletion` und `non_fast_forward`. Klassische Branch Protection ist nicht aktiv (`protection.enabled = false`), `required_status_checks` ist leer. Der Check `Build and test (Java 8 target)` ist also **nicht** verpflichtend; Merges ohne grünen Build sind technisch möglich.

In den GitHub-Branch-Regeln für `main` ist daher noch einzustellen:

- Pull Requests vor dem Merge verlangen,
- Statuschecks verlangen,
- den Check **`Build and test (Java 8 target)`** als verpflichtend auswählen,
- veraltete Branches vor dem Merge aktualisieren lassen, sofern dies zum gewünschten Merge-Workflow passt,
- Administrator-Bypass nur bewusst zulassen.

## Verifikation

1. ✅ Pull Request geöffnet: PR #46 (Session-0-Branch). PR #41 wird ohne Merge geschlossen, siehe `docs/analysis/ci-build-test-gap.md`.
2. ✅ Der Workflow **Build and test** startet auf das `pull_request`-Ereignis (Lauf 9, Check `Build and test (Java 8 target)`).
3. ✅ Unit-, Integrations- und ArchUnit-Tests werden ausgeführt (394 Tests, 0 Fehler).
4. ✅ Übersprungene Tests sind in der Actions-Zusammenfassung sichtbar (die beiden Exedra-Tests, namentlich).
5. Den erfolgreichen Check anschließend in den Branch-Regeln als verpflichtend auswählen.

Diese Repository-Einstellung kann nicht durch eine normale Commit-Datei erzwungen werden und muss über GitHub Settings beziehungsweise Rulesets vorgenommen werden.
