# TODO — verifizierte Alt-Branches löschen

**Stand:** 2026-07-19

## Aufgabe

Fünf verbliebene Copilot-Branches löschen, deren Pull Requests bereits gemergt wurden und deren Inhalte in `main` enthalten sind.

Die Branches erscheinen wegen Squash-Merges teilweise weiterhin als `ahead` oder `diverged`. Deshalb darf die Löschentscheidung nicht allein auf `git branch --merged` beruhen. Die inhaltsbasierte Prüfung und der PR-Merge-Status wurden bereits durchgeführt.

## Zu löschende Branches

- `copilot/analyze-mainframemate-authentication-flows`
- `copilot/migrate-credential-boundary`
- `copilot/define-core-contracts-virtual-resources`
- `copilot/migrate-lucene-indexing`
- `copilot/acropolis-implement-walking-skeleton`

## Sicherheitsprüfung vor der Löschung

Vor der Ausführung nochmals bestätigen:

1. Der zugehörige Pull Request ist gemergt.
2. Kein offener Pull Request verwendet einen dieser Branches als Head.
3. Es gibt keine branch-exklusiven Änderungen, die nicht bereits in `main` enthalten sind.
4. Der aktive Copilot-Branch für PR #41 (`copilot/verify-exedra-headless-test-behavior`) wird nicht gelöscht.

## Löschbefehl

In einem lokalen Clone mit Schreibzugriff auf `origin` ausführen:

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
git ls-remote --heads origin 'copilot/*'
```

Die fünf genannten Branches dürfen nicht mehr erscheinen. Der Branch von PR #41 und andere aktive Branches müssen erhalten bleiben.

## Optionaler Repository-Schutz gegen künftige Reste

Nach Abschluss prüfen, ob in den Repository-Einstellungen **Automatically delete head branches** aktiviert werden soll. Auch dann bleiben bei Squash-Merges inhaltliche Prüfungen sinnvoll, bevor man ältere manuell verbliebene Branches löscht.
