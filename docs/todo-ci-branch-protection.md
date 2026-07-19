# TODO — CI-Statuscheck für `main` verpflichtend machen

**Stand:** 2026-07-19

Der Workflow `.github/workflows/build.yml` führt den vollständigen Gradle-Build einschließlich `architecture-tests` bei Pull Requests und Pushes auf `main` aus.

Nach dem ersten erfolgreichen Workflow-Lauf ist in den GitHub-Branch-Regeln für `main` noch einzustellen:

- Pull Requests vor dem Merge verlangen,
- Statuschecks verlangen,
- den Check **`Build and test (Java 8 target)`** als verpflichtend auswählen,
- veraltete Branches vor dem Merge aktualisieren lassen, sofern dies zum gewünschten Merge-Workflow passt,
- Administrator-Bypass nur bewusst zulassen.

## Verifikation

1. Einen Pull Request öffnen oder PR #41 aktualisieren.
2. Prüfen, dass der Workflow **Build and test** startet.
3. Prüfen, dass Unit-, Integrations- und ArchUnit-Tests ausgeführt werden.
4. Prüfen, dass übersprungene Tests in der Actions-Zusammenfassung sichtbar sind.
5. Den erfolgreichen Check anschließend in den Branch-Regeln als verpflichtend auswählen.

Diese Repository-Einstellung kann nicht durch eine normale Commit-Datei erzwungen werden und muss über GitHub Settings beziehungsweise Rulesets vorgenommen werden.
