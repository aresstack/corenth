# TODO — veraltete Squash-Merge-Branches löschen

**Stand:** 2026-07-19

## Aufgabe

Die folgenden fünf bereits gemergten Copilot-Branches aus dem Remote-Repository löschen:

```text
copilot/analyze-mainframemate-authentication-flows
copilot/migrate-credential-boundary
copilot/define-core-contracts-virtual-resources
copilot/migrate-lucene-indexing
copilot/acropolis-implement-walking-skeleton
```

## Hintergrund

Die zugehörigen Pull Requests wurden per Squash-Merge in `main` übernommen. Deshalb erscheinen die Branches bei ancestry-basierten Prüfungen wie `git branch --merged` teilweise weiterhin als nicht gemergt, obwohl ihr Inhalt bereits in `main` enthalten ist.

Die Löschentscheidung wurde daher nicht allein anhand der Git-Historie getroffen, sondern anhand folgender Kriterien:

- der zugehörige Pull Request ist gemergt,
- kein offener Pull Request verwendet den Branch,
- ein inhaltsbasierter Vergleich hat keine Branch-only-Änderungen ergeben, die in `main` fehlen,
- der aktuelle offene Copilot-PR #41 verwendet einen anderen Branch (`copilot/verify-exedra-headless-test-behavior`).

## Ausführung

In einem lokalen Clone mit Schreibzugriff:

```bash
git push origin --delete \
  copilot/analyze-mainframemate-authentication-flows \
  copilot/migrate-credential-boundary \
  copilot/define-core-contracts-virtual-resources \
  copilot/migrate-lucene-indexing \
  copilot/acropolis-implement-walking-skeleton
```

Alternativ einzeln:

```bash
git push origin --delete copilot/analyze-mainframemate-authentication-flows
git push origin --delete copilot/migrate-credential-boundary
git push origin --delete copilot/define-core-contracts-virtual-resources
git push origin --delete copilot/migrate-lucene-indexing
git push origin --delete copilot/acropolis-implement-walking-skeleton
```

## Verifikation danach

```bash
git fetch --prune origin
git branch -r | grep 'origin/copilot/'
```

Die fünf oben genannten Branches dürfen danach nicht mehr erscheinen.

## Hinweis für künftige Squash-Merges

Nicht ausschließlich `git branch --merged origin/main` verwenden. Bei Squash-Merges stattdessen prüfen:

1. PR-Status `merged`,
2. kein offener PR auf dem Branch,
3. inhaltsbasierter Vergleich gegen aktuellen `main`,
4. erst danach Remote-Branch löschen.

Optional kann in den Repository-Einstellungen **Automatically delete head branches** aktiviert werden, damit gemergte PR-Branches künftig automatisch entfernt werden.
