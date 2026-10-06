# TODO — Verbleibende Copilot-Branches löschen

**Stand:** 2026-10-06 (Session 0; ersetzt die Fassung vom 2026-07-19, die nur die fünf per Squash gemergten Branches nannte)

Auf `origin` liegen zehn `copilot/*`-Branches, deren Pull Requests alle gemergt sind. Keiner enthält Änderungen, die nicht in `main` sind. Der elfte Branch `copilot/verify-exedra-headless-test-behavior` gehört zum offenen Draft-PR #41 und bleibt bestehen, bis #41 entschieden ist.

## Verifikation

Die Löschentscheidung darf bei Squash-Merges nicht allein auf `git branch --merged` beruhen. Geprüft wurde deshalb pro Branch (vollständige Historie, kein shallow clone):

1. PR-Status auf GitHub: `merged_at` gesetzt.
2. Merge-Commit-Branches: `git branch -r --merged origin/main` listet sie.
3. Squash-Branches: Diff zwischen Merge-Basis und Branch-Spitze ist patch-identisch mit dem Squash-Commit auf `main` (`git patch-id --stable`), und der Branch hat keine Commits nach `merged_at`.

| Branch | PR | Merge-Art | Nachweis in `main` |
| --- | ---: | --- | --- |
| `copilot/analyze-mainframemate-authentication-flows` | #19 | Squash | `6ceae66` |
| `copilot/migrate-credential-boundary` | #14 | Squash | `2803d13` |
| `copilot/define-core-contracts-virtual-resources` | #17 | Squash | `908a573` |
| `copilot/migrate-lucene-indexing` | #21 | Squash | `6b241d2` |
| `copilot/acropolis-implement-walking-skeleton` | #23 | Squash | `ee5a2e0` |
| `copilot/migrate-document-ingestion-and-detection` | #22 | Merge-Commit | `1bf81ee` |
| `copilot/migrate-opennlp-chunking` | #25 | Merge-Commit | `ff964b8` |
| `copilot/migrate-archive-cache-lifecycle` | #26 | Merge-Commit | `80eced4` |
| `copilot/migrate-generic-shell-framework` | #29 | Merge-Commit | `e158b36` |
| `copilot/define-mediated-bronze-access-model` | #32 | Merge-Commit | `a538612` |

## Löschen

Remote-Löschungen sind eine bewusste Administrationsaktion und werden nicht automatisiert ausgeführt:

```bash
git push origin --delete \
  copilot/analyze-mainframemate-authentication-flows \
  copilot/migrate-credential-boundary \
  copilot/define-core-contracts-virtual-resources \
  copilot/migrate-lucene-indexing \
  copilot/acropolis-implement-walking-skeleton \
  copilot/migrate-document-ingestion-and-detection \
  copilot/migrate-opennlp-chunking \
  copilot/migrate-archive-cache-lifecycle \
  copilot/migrate-generic-shell-framework \
  copilot/define-mediated-bronze-access-model
```

Danach lokal `git fetch --prune` und prüfen, dass `git branch -r | grep 'origin/copilot/'` nur noch den #41-Branch zeigt.
