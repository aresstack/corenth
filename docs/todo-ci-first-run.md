# TODO — Ersten Build-/Test-CI-Lauf verifizieren

**Stand:** 2026-10-06 (Session 0) · ursprünglich 2026-07-19

## Ergebnis

Der erste reale GitHub-Actions-Lauf von `.github/workflows/build.yml` ist verifiziert, allerdings nur für `push`-Ereignisse auf `main`:

- Läufe 6, 7 und 8 vom 2026-07-19 (Commits `2cd8b12`, `4c745e3`, `6eb39c3`) sind grün.
- Lauf 8 meldet 374 Tests, 0 Fehler, 0 Errors, 2 Skips; die Skips sind genau die zwei displaypflichtigen Exedra-Tests und erscheinen namentlich in der Job-Zusammenfassung.
- `architecture-tests` wird ausgeführt (`:architecture-tests:test` im Log).
- Exedra läuft headless ohne Xvfb.

## Noch offen

- ✅ Der `pull_request`-Nachweis liegt vor: PR #46 (Session-0-Branch) löste Lauf 9 aus, Check `Build and test (Java 8 target)` grün, 394 Tests, 0 Fehler, 0 Errors, 2 Skips (die beiden Exedra-Tests, namentlich). PR #41 besitzt weiterhin keine Check-Runs.
- Der Required Check ist nicht konfiguriert; siehe `docs/todo-ci-branch-protection.md`.

Details und die Entscheidung zu PR #41/#40: `docs/analysis/ci-build-test-gap.md`, Abschnitt „Stand 2026-10-06“.
