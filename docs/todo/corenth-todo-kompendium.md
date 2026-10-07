# Corenth — TODO-Kompendium: Implementierungspläne für alle offenen Issues

*Stand: 2026-07-19 · main @ b97c607+ · Session-0-Hinweise (2026-10-06) in Kapitel 1, 2, 3, 4, 9 und 13; Kapitel 3 in Session 1 (2026-10-07, #33) neu gefasst; Kapitel 1 ist durch die committete `build.yml` überholt · Java 8 · Alle Code-Skizzen sind gegen den realen Repo-Bestand verifiziert (Signaturen-Prüfung vom selben Tag); verifizierte Befunde sind in den Kapiteln als **Verifiziert:** markiert, offene Abgleiche explizit benannt.*

## Inhalt

1. CI: Gradle-Build & Architektur-Tests (#45)
2. Exedra: Headless-Verifikation in CI (#40)
3. Chalcotheca: Records, Historie, Lifecycle-Persistenz (#33)
4. Acropolis: Mediated Lifecycle & Run-Modell (#10)
5. Tamias: ChangeDetection, Invalidation, Scope (#5)
6. Deigma: Schwer-Extraktoren (#42)
7. Holkas/Mail: PST/OST-Connector (#36)
8. Holkas/NDV: Connector mit Session-Auth (#34)
9. Holkas/FTP: JES Submit & Spool (#35)
10. Holkas/Wiki: MediaWiki-Connector (#37)
11. Holkas/Confluence: Basic & mTLS (#38)
12. Holkas/SharePoint: SSO-first-Kaskade (#39)
13. Adyton: Interactive & persistente Secret-Sources (#43)
14. Pinakes: Semantic-Index-Ports (#7)
15. Propylaea: Source-Code-Modell (#3)
16. Katagogion: Plugin-/Tool-/MCP-Ports (#12)
17. Migration: Research-Disposition (#44)
18. Vergessene Migrationen aus MainframeMate

---

# Kapitel 1: CI: Gradle-Build & Architektur-Tests (#45)


**Datei:** `.github/workflows/build.yml` · **Auf `main` committet (9d64884, gehärtet 4c745e3) und bei Push grün (Läufe 6–8). `pull_request`-Lauf nachgewiesen (PR #46, Lauf 9). Offen vor #33/#10 Slice 2: Hinterlegung als verpflichtender Statuscheck — erst dann läuft jede weitere Änderung unter einem CI-Gate.**

> ⚠️ **Stand Session 0 (2026-10-06):** Dieses Kapitel wurde gegen `main @ b97c607` geschrieben, bevor `build.yml` committet wurde. Die committete Datei weicht von der Skizze unten ab: Workflowname „Build and test“, Job „Build and test (Java 8 target)“, Temurin JDK 21, `./gradlew --no-daemon clean build --stacktrace`, apt-Fontpakete für headless AWT, Python-Summary der JUnit-XML mit namentlicher Skip-Liste. **Maßgeblich ist die Datei im Repository, nicht diese Skizze.** Status: grün auf `main` (Läufe 6–8) und auf dem `pull_request`-Ereignis (PR #46, Lauf 9: 394 Tests, 0 Fehler, 2 Skips), Required Check nicht konfiguriert, CI also noch kein erzwungenes Merge-Gate. Details: `docs/analysis/ci-build-test-gap.md`.

### Designentscheidungen

1. **Ein Workflow, ein Job, `./gradlew build`.** `build` umfasst Kompilierung, alle Modultests **und** `architecture-tests` (ArchUnit) — kein separater Arch-Job nötig. Bewusst kein Matrix-Build (nur Java 8-Target, ein OS reicht: `ubuntu-latest`; Windows-spezifisches wie DPAPI/Windows-MY ist per Design skip-fähig, s. #38/#43).
2. **Runner-JDK 17, Bytecode-Ziel 1.8 — verifiziert:** Der Wrapper pinnt **Gradle 9.5.1** (braucht JDK ≥ 17 zum Ausführen), und `build.gradle` setzt `sourceCompatibility`/`targetCompatibility` auf `VERSION_1_8`. JDK 17 als Runner-JDK ist damit korrekt und kein Provisorium; javac 17 kompiliert Release-8-Bytecode. Ein separates JDK-8-Toolchain-Setup ist unnötig, solange keine JDK-8-spezifischen Bootclasspath-Probleme auftreten (dann Toolchains nachrüsten).
3. **Skipped-Events im Log** (Verzahnung mit #40/PR #41): kein CI-eigenes Log-Parsing; Sichtbarkeit kommt aus der Gradle-`testLogging`-Konfiguration.
4. **Concurrency-Gruppe pro Ref,** damit Force-Pushes alte Läufe abbrechen; Gradle-Cache über `actions/setup-java`-Built-in (`cache: gradle`) statt manueller Cache-Steps.
5. **Kein Release-Eingriff:** `chatgpt-compatible-release.yml` bleibt unberührt; der neue Workflow ist reiner Prüf-Workflow ohne `contents: write`.

### Implementierung

```yaml
# .github/workflows/build.yml
name: Build & Architecture Tests

on:
  pull_request:
  push:
    branches: [ main ]
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: build-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    name: Gradle build (tests + ArchUnit)
    runs-on: ubuntu-latest
    steps:
      - name: Check out repository
        uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          # Verifiziert: Wrapper = Gradle 9.5.1 (JDK >= 17 noetig);
          # Bytecode-Ziel bleibt 1.8 via source/targetCompatibility in build.gradle.
          java-version: '17'
          cache: gradle

      - name: Build, test, verify architecture
        run: ./gradlew --no-daemon build

      - name: Upload test reports on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: '**/build/reports/tests/test/'
          retention-days: 7
```

### ⚠️ Hinweis

Sollte `./gradlew build` im Repo-Ist-Zustand rot sein (ungetestete Direkt-Commits der letzten Wochen!), gilt: **erst grün machen, dann Workflow mergen** — ein von Tag 1 an roter Pflicht-Check wird ignoriert und erzieht alle Beteiligten falsch. Der allererste lokale Komplett-Build ist damit Teil dieses Issues.

### Akzeptanzkriterien

Workflow läuft bei Push auf `main` (Läufe 6–8 grün, 2026-07-19); `pull_request`-Trigger nachgewiesen (PR #46, Lauf 9 grün; PR #41 weiterhin ohne Check-Runs); ArchUnit nachweislich im Lauf enthalten (`:architecture-tests:test` im Log von Lauf 8); Reports-Artefakt `gradle-test-reports` bei Fehlschlag; Branch-Protection noch nicht konfiguriert — Required Check **`Build and test (Java 8 target)`** (nicht „Gradle build“) im Ruleset „main“ aktivieren, Umsetzung liegt beim Repo-Owner; übersprungene Tests erscheinen namentlich in der Actions-Summary (PR #41 damit redundant, wird nicht gemergt; Übergabe an #40).

---

# Kapitel 2: Exedra: Headless-Verifikation in CI (#40)


**Modul:** `proasteion:exedra` + CI · **Klein — CI aus #45 vorhanden (`build.yml`, Lauf 8 grün mit namentlicher Skip-Liste); PR #41 redundant, wird nicht gemergt; `pull_request`-Nachweis durch PR #46 erbracht**

> ⚠️ **Stand Session 0 (2026-10-06):** CI-Lauf 8 auf `main` bestätigt exedra 48 pass / 0 fail / 2 skip mit namentlicher Skip-Liste in der Actions-Summary (für alle Module, nicht nur exedra). PR #41 (`testLogging { events "skipped" }` nur in exedra) ist damit redundant und wird nicht gemergt; der unten skizzierte `HeadlessSkipBudgetTest` wurde nicht umgesetzt. Der `pull_request`-Nachweis liegt seit PR #46 (Lauf 9) vor. Entscheidung: `docs/analysis/ci-build-test-gap.md`.

### Designentscheidungen

1. **Die empirische Basis steht bereits:** Die lokale Headless-Verifikation (`docs/analysis/exedra-headless-test-verification.md`) hat 48 pass / 0 fail / 2 skip ergeben — die Guards sitzen exakt an den beiden `JFrame`-Tests, leichte Swing-Komponenten laufen headless. Es gibt **nichts umzubauen**; dieses Issue ist reine Verifikation + Sichtbarkeit.
2. **Skips müssen im CI-Log auffallen, nicht nur nicht-fehlschlagen.** PR #41 ergänzt `testLogging { events "skipped" }` — das ist der richtige Mechanismus. Zusätzlich (aus dem Verifikationsbericht §4): Die Erwartung „genau 2 Skips in exedra, 0 überall sonst" als Assertion festhalten, damit ein *stiller Anstieg* von Skips (z. B. ein Agent guarded reflexhaft weitere Tests) auffällt.
3. **Kein `xvfb` in der CI.** Verlockend, aber es würde die Guards wertlos machen und Display-Abhängigkeit verschleiern. Die CI bleibt bewusst display-los; GUI-Smoke-Tests wären ein separates, manuelles Profil (nicht in diesem Issue).

### Umsetzung

1. PR #41 ohne Merge schließen (redundant zur JUnit-XML-Zusammenfassung in `build.yml`, auf Exedra beschränkt; Entscheidung in `docs/analysis/ci-build-test-gap.md`).
2. Nach #45-Merge: CI-Lauf prüfen — erwartet: exedra 48/0/2, Skip-Zeilen im Log sichtbar.
3. Skip-Budget als Test verankern (leichtgewichtig, ohne Gradle-Plugin):

```java
// proasteion/exedra/src/test/java/.../HeadlessSkipBudgetTest.java
// Erklärt: Statt CI-seitiger Log-Auswertung prüft ein Meta-Test die Guard-Invariante:
// Genau die zwei bekannten Klassen tragen den Display-Guard. Neue Guards => Test schlägt fehl
// => bewusste Entscheidung nötig. Das ist robuster als Log-Parsing und läuft überall.
public class HeadlessSkipBudgetTest {
    // Verifiziert gegen den Bestand: genau diese zwei Testklassen erzeugen JFrames
    // und tragen den Display-Guard (48 pass / 2 skip im Headless-Lauf).
    private static final java.util.Set<String> EXPECTED_GUARDED = new java.util.HashSet<String>(
            java.util.Arrays.asList(
                    "ConfigurableToolbarExecutionTest",
                    "ShellFrameRegisterToolWindowTest"));

    @org.junit.Test
    public void onlyKnownTestsUseDisplayGuard() throws Exception {
        // Vergleich ueber einfache Klassennamen (Packages variieren innerhalb exedra):
        java.util.Set<String> guarded = GuardScanner.findSimpleClassNamesUsingDisplayGuard(
                "com.aresstack.corenth.proasteion.exedra");
        org.junit.Assert.assertEquals("Display-guarded test classes changed - review deliberately!",
                EXPECTED_GUARDED, guarded);
    }
}
```

*(`GuardScanner`: kleiner Classpath-Scan über die Test-Klassen des Moduls nach dem verwendeten `Assume`-Guard-Muster. Wenn das als Overkill empfunden wird: Minimalvariante = die Erwartung nur im Verifikationsdokument fixieren und Schritt 3 streichen — im PR entscheiden.)*

4. Issue mit Verweis auf Verifikationsdokument + CI-Lauf-Link schließen.

### Akzeptanzkriterien

CI-Lauf zeigt exedra-Skips explizit; Skip-Anzahl dokumentiert erwartet (2); keine Produktcode-Änderung; Verifikationsdokument aus `docs/analysis/` im Issue verlinkt.

---

# Kapitel 3: Chalcotheca: Records, Historie, Lifecycle-Persistenz (#33)


**Modul:** `astu:acropolis:chalcotheca` · **Java 8** · **Abhängigkeiten:** keine neuen (In-Memory-Referenz, H2 später als äußerer Adapter hinter demselben Port)

> ✅ **Stand Session 1 (2026-10-07): umgesetzt (lokaler Branch, noch nicht gemergt).** Die ursprüngliche Skizze dieses Kapitels (`BronzeResourceRecord`, zusätzlicher Port `ResourceLifecycleRepository` mit `RecordOutcome`, Enum `ACTIVE/STALE/TOMBSTONED`, `markStale`/`purge`) ist **verworfen**. Sie hätte eine dritte Modellwelt neben `ArchivedResource`/`ResourceArchiveRepository` und `ResourceArchive`/`ResourceSnapshot` erzeugt und mit `STALE` eine Tamias-Entscheidung als Ressourcenzustand persistiert. Umgesetzt wurde stattdessen die Konsolidierung der vorhandenen Typen.

### Abgrenzung

- **Chalcotheca #33 = Fakten und Historie:** Welche Ressource kennen wir, welche Bronze-Versionen wurden mit welchem `ResourceDigest` wann beobachtet, welche Version ist seit wann als indexiert vermerkt, wurde die Ressource an der Quelle als entfernt beobachtet.
- **Tamias #5 = Entscheidungen aus diesen Fakten:** neu beschaffen, Cache gültig/ungültig, neu indexieren, Withdrawal. Nichts davon wird im Record gespeichert.
- **Acropolis #10 = Ablauf und Ausführung dieser Entscheidungen** (Run-Modell, Cache-Konsolidierung in Slice 5).

### Modell: orthogonale Fakten statt linearer State Machine

Das frühere lineare `ResourceLifecycleState` (`PENDING → ACQUIRED → CACHED → INDEXED → STALE → TOMBSTONED`) ist **entfernt**. Es mischte Quellzustand, Cache-Präsenz, Indexierungsfakt und Policy-Urteil (`STALE`) in einer Dimension. Eine Ressource kann legitim gleichzeitig an der Quelle entfernt sein, Version 7 als zuletzt beobachtete und als indexierte Version haben und keinen Payload mehr im Cache besitzen.

| Typ | Rolle |
| --- | --- |
| `ArchivedResource` | Immutable Record je `VirtualResourceRef`: geordnete Versionshistorie (nie leer, Sequenzen `1..n` lückenlos, letzte = latest observed), optional `IndexedVersion`, optional `SourceRemoval`. Übergänge (`observe`, `markIndexed`, `withdrawIndexedVersion`, `markRemovedAtSource`) liefern neue Instanzen. Öffentlicher Konstruktor dient der Rekonstitution durch Persistenzadapter und prüft die Invarianten. |
| `ResourceVersion` | Immutable: monotone `sequence` (vom Record vergeben, unabhängig von Zeitstempeln), bestehender `ResourceDigest`, `observedAtMillis`. Keine Bytes. |
| `IndexedVersion` | Fakt „diese Version ist indexiert, seit …". Muss Teil der Historie sein. |
| `SourceRemoval` | Tombstone-Beobachtung mit Zeitpunkt. Unabhängig vom Indexierungsfakt. |

Digest-Semantik: Gleichheit bleibt über `ResourceDigest` (`ResourceFingerprint`/SHA-256 + `sizeBytes`) definiert; der Bronze-Beschaffer erzeugt den Digest, Chalcotheca vergleicht nur. Identischer Digest zur zuletzt beobachteten Version erzeugt keine neue Version; ein geänderter Digest erzeugt Version `n + 1` (auch bei Rückkehr zu einem früheren Inhalt). Eine erneute Beobachtung nach einem Tombstone hebt die Entfernung auf (die Ressource ist wieder an der Quelle), ohne bei gleichem Digest eine Version zu erzeugen.

**Nicht persistiert:** Cache-Präsenz (`CACHED`), `STALE`, `DENIED`, `FAILED`, `BLACKLISTED`, `REQUIRE_AUTH`, `REQUIRE_SOURCE_CHECK`, `ALLOW_CACHED_ONLY`, `shouldReindex`/`shouldReacquire` und andere request- oder actor-bezogene Entscheidungen. Access-Denials hängen vom Akteur ab, die Indexing-Rule-Verweigerung von der momentanen Policy-Konfiguration; `FAILED` kann später im #10-Run-/Diagnosemodell sinnvoll werden.

### Port

`ResourceArchiveRepository` ist der **einzige** Persistence-Port für Records/Historien: `save(ArchivedResource)`, `findByRef(VirtualResourceRef)`, `findByUri(BookmarkUri)` (alle Kinds, Reihenfolge des ersten Speicherns). Kein Hard-Delete, kein `findByState`. `InMemoryResourceArchiveRepository` ist die deterministische Referenz (`LinkedHashMap`, synchronisiert). `ResourceArchiveRepositoryContractTest` beschreibt den Vertrag, den auch ein späterer H2-Adapter erfüllen muss.

### `ResourceArchive` als temporäre Kompatibilitätsfassade

`RecordBackedResourceArchive` implementiert `ResourceArchive` über dem Repository; `InMemoryResourceArchive` komponiert sie mit dem In-Memory-Repository und legt die Records über `records()` offen. `ResourceSnapshot` bleibt als Wert-/Sichtobjekt der Fassade und wird **nicht** separat gespeichert. Coordinator und `MediatedResourceService` sind nicht umverdrahtet (das ist #10 Slice 5).

| Operation | Semantik |
| --- | --- |
| `store(snapshot)` | Digest beobachten (neue Version nur bei Änderung), diese Version als indexiert markieren; `indexedAtMillis` ist Indexierungszeit und, bei neuer Version, Beobachtungszeit. |
| `hasChanged(ref, digest)` | Reine Faktenabfrage: ohne Indexierungsfakt `true`, sonst Digest-Vergleich mit der indexierten (nicht der zuletzt beobachteten) Version. |
| `remove(ref)` | Nimmt nur den Indexierungsfakt zurück; Record und Historie bleiben. Danach `find == null`, `hasChanged == true`. |
| `removeByUri(uri)` | Kind-agnostisch: Tombstone für alle Records der URI; Historie und Indexierungsfakt bleiben. `true`, wenn mindestens ein Record existiert. Zeitpunkt aus injizierter `java.time.Clock`. |
| `find(ref)` / `findByUri(uri)` | Nur indexierte Sicht; nie Rückfall auf die zuletzt beobachtete Version. |

Konsequenz für `MediatedResourceService.deleteEntry`: es entfernt die Payload-Caches und setzt den Tombstone, nimmt aber den Indexierungsfakt nicht zurück (der Lexical-Index wird dort ohnehin nicht angefasst). `MediatedResourceServiceTest.deleteEntry_tombstonesArchiveRecord_forDirectoryResource_keepingHistoryAndIndexedFact` hält das fest (vorher: `findByUri == null`).

### Payload-Caches

Unverändert. `BronzeContent` ist Payload, der Record ist ein Fakt über den Payload; eine History ohne Bytes ersetzt keinen Content-Cache, und Cache-Präsenz gehört nicht in den Record. Invalidierung entscheidet #5, die Konsolidierung der drei Service-Caches führt #10 Slice 5 aus.

### Übergabetests

- `knownGap_changedSourceContent_isServedFromCounterCacheUntilInvalidationExists`, `knownGap_oversizedFile_isAcquiredAndRetainedByCounterBeforeDenial`, `knownGap_blacklistedAfterIndexing_staysInTheIndex_untilAWithdrawalPathExists`: unverändert Known Gaps (#5/#10).
- `deniedThenReaccepted_unchangedContent_reindexes` und `noTextThenReprocessed_unchangedContent_reindexes`: grün aus dem fachlichen Grund (nur Indexierungsfakt zurückgenommen, Historie erhalten, daher `hasChanged == true`); beide prüfen das jetzt explizit.

### Offen für #5 / #10 und vor dem H2-Adapter

- #5: Change-Detection/Invalidation/Withdrawal-Entscheidungen auf Basis von `latestObservedVersion`, `indexedVersion` und `sourceRemoval`; Tombstone + indexiert ist ein Withdrawal-Kandidat.
- #10 Slice 5: Coordinator/Run-Modell direkt gegen `ResourceArchiveRepository` (Beobachtung bei Beschaffung statt erst bei Indexierung, `UNCHANGED` aus dem Record), Service-Caches konsolidieren, `ResourceArchive`/`ResourceSnapshot` danach entfernen.
- Vor H2: Transaktions-/Optimistic-Locking-Grenze für Read-Modify-Write (heute `synchronized` je Fassade); Zeitstempelquelle vereinheitlichen (Snapshot-Zeit vs. `Clock`, `BronzeContent.acquiredAt`); Retention/Purge-Regel für Historien (heute kein Löschpfad); ob Tombstone-Historie (mehrfaches Entfernen/Wiederauftauchen) statt nur der aktuellen Beobachtung benötigt wird; Tabellenschlüssel `(uri, kind)` plus Sequenz.

### Out of scope / do-not-copy

Kein H2/JDBC, keine Tamias-Policy, keine Cache-Invalidierung, kein Umbau von `MediatedResourceService`, keine Content-Bytes in der Historie. Kein Kopieren von MainframeMates `CacheRepository`-SQL oder `ArchiveRun`-Swing-Progress.

---

# Kapitel 4: Acropolis: Mediated Lifecycle & Run-Modell (#10)


**Modul:** `astu:acropolis` (+ Bootstrap-Modul `proasteion:application`, s. ADR-0001) · **Java 8** · **Abhängigkeiten:** Slice 1–3 unabhängig von #33 und #5 (Slice 1 erledigt; Slice 2 parallel zu #33 startbar, vgl. Inventar §5); das Run-Modell (Slice 4) folgt nach #5, erst Slice 5 integriert den #33-Repository-Port (`UNCHANGED`/Tombstone) und die #5-Entscheidungen

> ✅ **Stand Session 0 (2026-10-06): Slice 1 und ADR umgesetzt.** `ResourceLifecycleCoordinator` beschafft nur noch über den neuen Chalcotheca-Vertrag `MediatedResourceAccess` (implementiert von `MediatedResourceService`); `RawResourceProvider`/`FetchedResource` sind entfernt; ArchUnit-Regel `ACROPOLIS_LIFECYCLE_MUST_ACQUIRE_THROUGH_MEDIATED_ACCESS`; Bootstrap-Ort in `docs/adr/0001-composition-root.md` entschieden (`proasteion:application`). **Nicht** umgesetzt und gemäß Issue-Text spätere Slices: produktiver Kompositionspunkt und erste produktive `ResourceAccessPolicy` (Slice 2), `AccessPreparation`/Adyton-Station (Slice 3), Run-Modell (Slice 4), Cache-Konsolidierung (Slice 5). Abweichend von Designentscheidung 1 unten gehört der Kompositionspunkt nach Issue-Text zu Slice 2, nicht zu Slice 1. Der `ContentInspector`-Port bleibt; die Harbor-Variante (`emporion.ResourceHarbor`) beschafft außerhalb der Mediation und ist deshalb keine Lifecycle-Eingabe.

### Designentscheidungen

1. **Reihenfolge ist verbindlich (aus Issue-Text übernommen):** Slice 1 (umgesetzt, Session 0) stellt ausschließlich die Beschaffung des `ResourceLifecycleCoordinator` auf den Chalcotheca-Vertrag `MediatedResourceAccess` um — nicht auf `MediatedResourceService`/`AcquisitionPort`, von denen Acropolis per ArchUnit (`ACROPOLIS_LIFECYCLE_MUST_ACQUIRE_THROUGH_MEDIATED_ACCESS`) nicht abhängen darf. Vor Slice 2 ist der Ort des Kompositionspunkts per ADR entschieden (ADR-0001); Slice 2 schafft den produktiven äußeren Kompositionspunkt; Slice 3 ergänzt die Adyton-Station. Erst Slice 4 extrahiert das Run-Modell, Slice 5 integriert #33/#5. Grund: Ein Run-Modell, das um den `RawResourceProvider`-Direktpfad herum entsteht, zementiert den falschen Zugriffsweg.
2. **Der Coordinator wird nicht ersetzt, sondern intern umgehängt.** Öffentliche Signatur bleibt vorerst; nur die Beschaffung wechselt. Der bestehende `WalkingSkeletonIntegrationTest` bleibt das Regressionsnetz und wird lediglich auf die neue Verdrahtung umgestellt.
3. **Adyton-Station (Slice 3) als optionale Vorbereitung, nicht als Pflichtdurchlauf.** `file:`-Ressourcen brauchen keine Credentials. Die Station ist ein Port `AccessPreparation`, den authentifizierungspflichtige Connectors nutzen werden (FTP/MVS, sobald ein produktiver FTP-Transport existiert — heute nur Ports und Test-Fakes; NDV/Wiki später); die Default-Implementierung ist ein No-Op. Slice 1 kommt ohne diesen Port aus. Es fließen ausschließlich `AccessRequest`/Grant-Konzepte — niemals `SecretMaterial` — durch Acropolis (ArchUnit-Secret-Regeln decken das bereits ab).
4. **Run-Modell klein schneiden:** `ResourceProcessingRun`, `StepOutcome`, `RunSummary`, `ProcessingFailure`. Kein `Plan`, kein `StepType`-Katalog, kein `Context`-Objekt im ersten Wurf — die tauchen erst auf, wenn ein zweiter Ablauftyp existiert (YAGNI, konsistent zur #13-Entscheidung). Das weicht bewusst vom älteren Juni-Plan ab und ist mit dem neuen #10-Text vereinbar („Run-/Plan/Step-Modell extrahieren" ≠ alles auf einmal).
5. **`UNCHANGED` kommt aus den #33-Fakten, nicht aus eigener Logik:** Grundlage sind die `ArchivedResource`-Records hinter `ResourceArchiveRepository` (bis Slice 5 über die Fassade `ResourceArchive.hasChanged`). Ob neu indexiert wird, entscheidet #5; keine zweite Digest-Vergleichslogik im Coordinator. *Stand Session 1: Der früher skizzierte `ResourceLifecycleRepository`/`RecordOutcome` existiert nicht und ist verworfen (Kapitel 3).*

### Bootstrap-Entscheidung (ADR-Pflicht aus dem Issue)

**Empfehlung: neues Modul `proasteion:application`.** Begründung: Es ist ein *Adapter nach innen* (verdrahtet Ports mit konkreten Implementierungen) und gehört damit in den Außenring; `proasteion`-Root bleibt gemäß YAGNI-Entscheidung klassenfrei (das Application-Modul ist ein *Submodul*, kein Root-Vokabular); Exedra-Bindung würde die Komposition an Swing koppeln und Headless-/CLI-Betrieb (CI, spätere Server-Nutzung) verbauen. **Verworfene Alternative** (im ADR dokumentieren): Komposition in `exedra` — abgelehnt wegen Swing-Kopplung und weil `katagogion`-Tools denselben Kompositionspunkt brauchen werden. ADR-Datei: `docs/adr/0001-composition-root.md`.

### ⚠️ Konflikte / Probleme gefunden

- **Doppelte Caches:** `MediatedResourceService` hält drei unbegrenzte `ConcurrentHashMap`-Caches. Sie müssen entfernt oder gegen die #33-Records (`ResourceArchiveRepository`) und die #5-Invalidierung konsolidiert werden, sonst existieren zwei Wahrheiten (Detail in Kapitel 3, „Architektur-Konflikt“). Empfehlung: entfernen; Cache-Nutzen kommt aus `ResourceLifecycleRepository` + tamias-Entscheid. *Stand Session 0: in Slice 1 bewusst belassen und dokumentiert; Konsolidierung erst gegen den #33-Vertrag (Slice 5).*
- **`ContentInspector` vs. `deigma`:** Der Coordinator nutzt einen eigenen `ContentInspector`-Port, `emporion` nutzt `deigma`. Zwei Extraktionswege. Slice 1 sollte den Coordinator-Input auf das `HarborResult` von `emporion.ResourceHarbor` umstellen (Harbor = Beschaffung + flache Extraktion), statt beide Wege zu pflegen.
- **`AcquisitionPort` wirft `IOException`,** `MediatedResourceService` fängt sie in `MediatedResult`. Der Coordinator muss `MediatedResult`-Fehler in `ProcessingFailure` überführen — nicht in Exceptions, damit ein Run bei Einzelfehlern weiterläuft.
- **`ResourceAccessPolicy` existiert nur als Interface + Testimplementierung** (verifiziert): Der produktive Mediated-Pfad war bislang schlicht nicht konfigurierbar. Slice 2 (Kompositionspunkt) muss die erste produktive `ResourceAccessPolicy` explizit wählen und dokumentieren — eine „erlaube alles“-Policy nur als bewusst benannter Platzhalter (ADR-0001, Leitplanke 5); in Slice 1 wurde keine `PermitAllAccessPolicy` ergänzt. Die echte Policy-Komposition kommt mit #5. Auch das gehört ins tamias-Inventar, sobald es entsteht.

### Slice 1 — Mediated Acquisition (umgesetzt in Session 0) und Slice 3 — Adyton-Station (offen)

#### `AccessPreparation` (Port, `astu:acropolis`) — Slice 3, noch nicht vorhanden

Erklärt: die adyton-Station. Sie liefert dem Lifecycle nichts außer der Zusicherung „Zugang ist vorbereitet" (oder eine Failure). Rückgabetyp bewusst `void` + Exception statt Grant-Durchreichung — der Coordinator soll Grants weder halten noch weitergeben können.

```java
package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.BookmarkUri;

public interface AccessPreparation {
    /** Ensures delegated access for the target of the given uri is available.
     *  No-op for schemes that need no credentials. Must never expose secrets. */
    void prepare(BookmarkUri uri) throws AccessPreparationException;

    AccessPreparation NONE = new AccessPreparation() {
        @Override public void prepare(BookmarkUri uri) { /* no-op */ }
    };
}
```

```java
package com.aresstack.corenth.astu.acropolis;

public class AccessPreparationException extends Exception {
    public AccessPreparationException(String message, Throwable cause) { super(message, cause); }
}
```

#### Umbau `ResourceLifecycleCoordinator` (Kernänderung, Auszug)

Erklärt: Beschaffung läuft über den schmalen Vertrag `MediatedResourceAccess.readContent(ResourceAccessRequest)` (implementiert von `MediatedResourceService`; `fetchContent` ist die interne `AcquisitionPort`-Methode und bleibt für den Coordinator unsichtbar); `RawResourceProvider` wird aus dem Konstruktor entfernt (Breaking Change nur für Tests — akzeptiert, da kein Produktivnutzer existiert). Der `ResourceAccessRequest` wird mit `ActorIdentity` „lifecycle" gebildet, damit tamias Lifecycle-Zugriffe von UI-Zugriffen unterscheiden kann.

```java
// Konstruktor (Slice 1, umgesetzt in Session 0 — tatsächliche Signatur):
public ResourceLifecycleCoordinator(MediatedResourceAccess mediatedAccess,   // Vertrag, implementiert von MediatedResourceService
                                    ActorIdentity actor,                     // Lifecycle-Akteur für ResourceAccessRequest
                                    ContentInspector contentInspector,       // Port bleibt; Deigma-Adapter am Kompositionspunkt
                                    ResourcePolicy policy,
                                    ResourceArchive archive,                 // bestehender Port; #33-Repository erst in Slice 5
                                    LexicalIndex lexicalIndex,
                                    LexicalChunker lexicalChunker) { ... }
// AccessPreparation (Slice 3), ResourceArchiveRepository (#33-Port, direkt erst in Slice 5) und eine
// Harbor-Inspektion sind bewusst NICHT Teil der Signatur; die ursprüngliche Skizze
// (MediatedResourceService + AccessPreparation + ResourceLifecycleRepository +
// ResourceHarborInspection) gilt nicht mehr.

// Beschaffungspfad neu (statt resourceProvider.fetch(...)):
accessPreparation.prepare(uri);
// Verifiziert: die Methode heisst readContent, und ResourceAccessRequest liegt im
// tamias-Paket mit Konstruktor (ActorIdentity, BookmarkUri, ResourceOperation):
MediatedResult<BronzeContent> fetched = mediatedAccess.readContent(
        new ResourceAccessRequest(lifecycleActor, uri, ResourceOperation.READ_CONTENT));
if (!fetched.isSuccess()) { return StepOutcome.denied(fetched.decision()); }
// Stand Session 1: kein recordAcquisition/RecordOutcome. Slice 5 beobachtet die Version am
// ArchivedResource (observe) und speichert sie über ResourceArchiveRepository; ob UNCHANGED
// gilt, ergibt sich aus indexedVersion vs. Digest (Entscheidung #5).
```

*(`ResourceHarborInspection` ist ein schmaler Acropolis-Port, den `proasteion:application` mit `emporion.DefaultResourceHarbor` adaptiert — Acropolis darf `proasteion` nicht importieren; die Richtung bleibt proasteion → astu.)*

```java
package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeContent;

// HINWEIS: deigma-Typen dürfen nicht in astu importiert werden (ArchUnit-Richtung
// proasteion -> astu). Deshalb definiert acropolis einen minimalen eigenen Ergebnistyp;
// der Adapter in proasteion:application mappt deigma-Resultate darauf.
public interface ResourceHarborInspection {
    InspectedText inspect(BronzeContent content);
    final class InspectedText {
        private final String title; private final String text; private final String contentType;
        public InspectedText(String title, String text, String contentType) {
            this.title = title; this.text = text; this.contentType = contentType; }
        public String title() { return title; }
        public String text() { return text; }
        public String contentType() { return contentType; }
    }
}
```

#### Kompositionspunkt `proasteion:application` — Slice 2 (nach ADR-0001; offen)

Erklärt: der erste produktive Bootstrap. Bewusst eine einzige Klasse ohne Framework; DI-Container erst bei Bedarf. Verdrahtet werden die in Slice 1 eingeführten Verträge (`MediatedResourceAccess` ← `MediatedResourceService`, `AcquisitionPort` ← `HolkasAcquisitionPort`, `ContentInspector` ← Deigma-Adapter, bestehendes `ResourceArchive`); die erste produktive `ResourceAccessPolicy` muss hier explizit gewählt werden (ADR-0001, Leitplanke 5). `PermitAllAccessPolicy` und `DeigmaHarborInspection` in der Skizze unten existieren noch nicht, `InMemoryResourceLifecycleRepository` ist verworfen (heute: `InMemoryResourceArchive` über `InMemoryResourceArchiveRepository`, Kapitel 3), und der skizzierte Coordinator-Aufruf entspricht nicht der umgesetzten Signatur.

```java
package com.aresstack.corenth.proasteion.application;

public final class CorenthBackend {
    public static ResourceLifecycleCoordinator createFileLifecycle(java.nio.file.Path indexDir) {
        ResourceConnectorRegistry connectors = new DefaultResourceConnectorRegistry();
        connectors.register(new FileSystemResourceConnector());
        AcquisitionPort acquisition = new HolkasAcquisitionPort(connectors);
        InMemoryResourceLifecycleRepository repo = new InMemoryResourceLifecycleRepository();
        // Verifizierter Befund: ResourceAccessPolicy (tamias, evaluate(ResourceAccessRequest))
        // hat bisher KEINE Produktiv-Implementierung — nur eine Testklasse. PatternResourcePolicy
        // implementiert das andere Interface (ResourcePolicy) und passt hier nicht.
        // Slice 1 liefert daher die erste produktive Implementierung mit: eine schlichte
        // PermitAllAccessPolicy in tamias (jede Anfrage -> allow), die #5 spaeter durch
        // die komponierten Policies ersetzt.
        ResourceAccessPolicy access = new PermitAllAccessPolicy();
        MediatedResourceService mediated = new MediatedResourceService(access, acquisition, repo);
        ResourceHarborInspection inspection = new DeigmaHarborInspection(new DefaultResourceHarbor(...));
        return new ResourceLifecycleCoordinator(mediated, AccessPreparation.NONE, repo,
                inspection, defaultPolicy(), openIndex(indexDir), defaultChunker());
    }
}
```

### Slice 4 — Run-Modell (nach Slice 2/3 und nach #5; `UNCHANGED`/Tombstone mit #33/#5 in Slice 5)

#### `StepOutcome` / `RunSummary` / `ResourceProcessingRun`

Erklärt: `StepOutcome` ist das Ergebnis *einer* Ressource; ein Run aggregiert viele. Failure trägt Reason-Text + Kategorie (maschinenlesbar, analog tamias-ReasonCodes). Keine Steps-innerhalb-Ressource-Modellierung im ersten Wurf.

```java
package com.aresstack.corenth.astu.acropolis;

public final class StepOutcome {
    public enum Kind { INDEXED, UNCHANGED, DENIED, TOMBSTONED, FAILED }
    private final Kind kind; private final String reason;
    private StepOutcome(Kind kind, String reason) { this.kind = kind; this.reason = reason; }
    public static StepOutcome indexed() { return new StepOutcome(Kind.INDEXED, null); }
    public static StepOutcome unchanged() { return new StepOutcome(Kind.UNCHANGED, null); }
    public static StepOutcome denied(Object decision) { return new StepOutcome(Kind.DENIED, String.valueOf(decision)); }
    public static StepOutcome failed(String reason) { return new StepOutcome(Kind.FAILED, reason); }
    public Kind kind() { return kind; }
    public String reason() { return reason; }
}
```

```java
package com.aresstack.corenth.astu.acropolis;

import com.aresstack.corenth.astu.VirtualResourceRef;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ResourceProcessingRun {
    private final String runId = UUID.randomUUID().toString();
    private final long startedAtMillis = System.currentTimeMillis();
    private final Map<VirtualResourceRef, StepOutcome> outcomes =
            new LinkedHashMap<VirtualResourceRef, StepOutcome>();

    public String runId() { return runId; }
    public void record(VirtualResourceRef ref, StepOutcome outcome) { outcomes.put(ref, outcome); }
    public Map<VirtualResourceRef, StepOutcome> outcomes() { return Collections.unmodifiableMap(outcomes); }

    public RunSummary summarize() {
        EnumMap<StepOutcome.Kind, Integer> counts =
                new EnumMap<StepOutcome.Kind, Integer>(StepOutcome.Kind.class);
        for (StepOutcome o : outcomes.values()) {
            Integer c = counts.get(o.kind());
            counts.put(o.kind(), c == null ? 1 : c + 1);
        }
        return new RunSummary(runId, startedAtMillis, System.currentTimeMillis(), counts);
    }
}
```

*(`RunSummary` als schlichtes Wertobjekt mit den Zählern; `ProcessingFailure` geht als `StepOutcome.failed(reason)` auf — eigene Klasse erst bei Bedarf an Stacktrace-Transport.)*

### Tests

Slice 1 (✅ Session 0): Walking-Skeleton-Integrationstest auf neue Verdrahtung (`MediatedResourceAccess` ← `MediatedResourceService` ← `HolkasAcquisitionPort`) umgestellt und grün; DENY der AccessPolicy erzeugt `DENIED` statt Exception; ArchUnit-Regel `ACROPOLIS_LIFECYCLE_MUST_ACQUIRE_THROUGH_MEDIATED_ACCESS` grün; kein `RawResourceProvider`/`FetchedResource` mehr im Produktionscode. Slice 2: Kompositionspunkt verdrahtet den `file:`-Lifecycle ohne Testcode. Slice 3: `AccessPreparation.NONE` für `file:`, REQUIRE_AUTH-Pfad mit Fake-Strategie. Slice 4: Summary zählt INDEXED/UNCHANGED/DENIED/FAILED korrekt; Einzelfehler bricht Run nicht ab; RunId eindeutig. Slice 5: UNCHANGED via zweitem Lauf über #33-Repo.

### Out of scope

Kein Scheduler, keine Parallelisierung, kein Persistieren des Run-Status (Folge-Issue nach #33-H2), kein pinakes-Hook (nur Kommentar-Markierung), keine UI.

### Akzeptanzkriterien

ADR committed ✅ (Session 0); `RawResourceProvider`-Direktpfad entfernt ✅ (Session 0); Service-Caches entfernt oder auf Repository umgestellt (Slice 5, nach #33); `proasteion:application` existiert und verdrahtet den `file:`-Lifecycle produktiv (Slice 2); alle ArchUnit-Regeln grün ✅; Inventar §1 „Zentrale Lücke" aufgelöst (nach Slice 2).

---

# Kapitel 5: Tamias: ChangeDetection, Invalidation, Scope (#5)


**Modul:** `astu:acropolis:chalcotheca:tamias` · **Java 8** · **Abhängig von:** #33 (liefert Digest/Version als Input)

### Designentscheidungen

1. **Tamias bleibt rein: Entscheidungen ohne eigene Datenhaltung.** ChangeDetection bekommt Stored-Digest und Current-Digest als *Parameter* — tamias liest nie selbst aus dem Archiv. Damit bleibt die ArchUnit-Rolle „Policy-Steward" trivially erfüllt und jede Strategie ist ohne Repository testbar. Der Aufrufer (#10-Coordinator) beschafft beide Werte.
2. **Kein `IndexSource`-God-Object.** Die MainframeMate-Felder (`scopePaths`, `includePatterns`, `excludePatterns`, `maxDepth`, `maxFileSizeBytes`, `changeDetection`) werden in drei unabhängige, einzeln testbare Policies zerlegt: `ResourceScopePolicy` (wo/wie tief), `ChangeDetectionStrategy` (neu genug?), `CacheInvalidationPolicy` (wann Bronze verwerfen). Kombination per Komposition im Aufrufer, nicht per Sammelobjekt.
3. **Jede Entscheidung trägt einen maschinenlesbaren ReasonCode** — konsistent zum bestehenden `AccessReasonCode`-Muster. Neue Codes erweitern das bestehende Enum-Muster, ersetzen es nicht.
4. **ChangeDetection-Modi als Strategie, nicht als Enum-Switch:** `DIGEST` (Standard), `METADATA` (mtime+size, für teure Fetches), `ALWAYS` (Force-Reindex). MainframeMates `scheduleMode`/`securityMode` werden bewusst **nicht** übernommen — Scheduling ist Orchestrierung (#10-Folge), Security ist AccessPolicy (existiert).
5. **Scope arbeitet auf `BookmarkUri`-Pfadsegmenten, nicht auf `java.io.File`** — damit gilt dieselbe Tiefen-/Pattern-Logik für `file:`, `ftp:`, `ndv:` usw.

### ⚠️ Problem gefunden

`ResourcePolicy.evaluate(VirtualResourceRef, long sizeBytes)` (bestehend) und die neuen Policies überlappen bei `maxFileSizeBytes`. **Entscheidung:** Größe wandert in `ResourceScopePolicy`; das bestehende `ResourcePolicy` bleibt in #10 Slice 1 unverändert (dort zweimal ausgewertet: vor der Beschaffung ohne Größe, danach mit realer Größe), wird in #10 Slice 5 (nach #5) durch die Scope-Policy ersetzt und danach deprecated. Im PR dokumentieren, nicht doppelt prüfen.

### Klassen

#### `ChangeDecision` + `ChangeReasonCode`

```java
package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

public enum ChangeReasonCode {
    FIRST_ACQUISITION, DIGEST_CHANGED, DIGEST_UNCHANGED,
    METADATA_CHANGED, METADATA_UNCHANGED, FORCED_REFRESH
}
```

```java
package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

public final class ChangeDecision {
    private final boolean reindex;
    private final ChangeReasonCode reason;
    private ChangeDecision(boolean reindex, ChangeReasonCode reason) {
        this.reindex = reindex; this.reason = reason;
    }
    public static ChangeDecision reindex(ChangeReasonCode r) { return new ChangeDecision(true, r); }
    public static ChangeDecision skip(ChangeReasonCode r) { return new ChangeDecision(false, r); }
    public boolean shouldReindex() { return reindex; }
    public ChangeReasonCode reason() { return reason; }
}
```

#### `ChangeDetectionStrategy` (Port) + Implementierungen

Erklärt: `ObservedResourceState` ist der schmale Input-DTO (Digest optional, Metadaten optional), damit `METADATA`-Modus ohne Content-Fetch entscheiden kann — genau der Punkt, der bei FTP/NDV Roundtrips spart. `null` als „unbekannt/erste Beschaffung".

```java
package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

import com.aresstack.corenth.astu.acropolis.chalcotheca.ResourceDigest;

public interface ChangeDetectionStrategy {

    final class ObservedResourceState {
        private final ResourceDigest digest; private final Long lastModifiedMillis; private final Long sizeBytes;
        public ObservedResourceState(ResourceDigest digest, Long lastModifiedMillis, Long sizeBytes) {
            this.digest = digest; this.lastModifiedMillis = lastModifiedMillis; this.sizeBytes = sizeBytes; }
        public ResourceDigest digest() { return digest; }
        public Long lastModifiedMillis() { return lastModifiedMillis; }
        public Long sizeBytes() { return sizeBytes; }
    }

    /** @param stored state from the bronze record, or null if unknown
     *  @param observed freshly observed state (metadata and/or digest) */
    ChangeDecision evaluate(ObservedResourceState stored, ObservedResourceState observed);
}
```

```java
package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

public final class DigestChangeDetection implements ChangeDetectionStrategy {
    @Override public ChangeDecision evaluate(ObservedResourceState stored, ObservedResourceState observed) {
        if (stored == null || stored.digest() == null)
            return ChangeDecision.reindex(ChangeReasonCode.FIRST_ACQUISITION);
        if (observed == null || observed.digest() == null)
            return ChangeDecision.reindex(ChangeReasonCode.FORCED_REFRESH); // cannot verify -> be safe
        return stored.digest().equals(observed.digest())
                ? ChangeDecision.skip(ChangeReasonCode.DIGEST_UNCHANGED)
                : ChangeDecision.reindex(ChangeReasonCode.DIGEST_CHANGED);
    }
}
```

```java
package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

public final class MetadataChangeDetection implements ChangeDetectionStrategy {
    @Override public ChangeDecision evaluate(ObservedResourceState stored, ObservedResourceState observed) {
        if (stored == null) return ChangeDecision.reindex(ChangeReasonCode.FIRST_ACQUISITION);
        if (observed == null || observed.lastModifiedMillis() == null || observed.sizeBytes() == null)
            return ChangeDecision.reindex(ChangeReasonCode.FORCED_REFRESH);
        boolean same = observed.lastModifiedMillis().equals(stored.lastModifiedMillis())
                && observed.sizeBytes().equals(stored.sizeBytes());
        return same ? ChangeDecision.skip(ChangeReasonCode.METADATA_UNCHANGED)
                    : ChangeDecision.reindex(ChangeReasonCode.METADATA_CHANGED);
    }
}
```

*(Dritte Implementierung `AlwaysReindex` trivial: immer `FORCED_REFRESH`.)*

#### `ResourceScopePolicy`

Erklärt: fasst Include/Exclude (Glob auf URI-Pfad), `maxDepth` (Segmentzählung unterhalb der Scope-Wurzel) und `maxSizeBytes` zusammen. Nutzt das bestehende `PatternResourcePolicy`-Matching-Muster; Rückgabe ist der bestehende `PolicyReason`-Typ, um den #10-Umbau nahtlos zu machen.

```java
package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

import com.aresstack.corenth.astu.BookmarkUri;

public final class ResourceScopePolicy {
    private final BookmarkUri scopeRoot;
    private final java.util.List<String> includeGlobs;
    private final java.util.List<String> excludeGlobs;
    private final int maxDepth;           // -1 = unlimited
    private final long maxSizeBytes;      // -1 = unlimited

    public ResourceScopePolicy(BookmarkUri scopeRoot, java.util.List<String> includeGlobs,
                               java.util.List<String> excludeGlobs, int maxDepth, long maxSizeBytes) {
        if (scopeRoot == null) throw new IllegalArgumentException("scopeRoot required");
        this.scopeRoot = scopeRoot;
        this.includeGlobs = copy(includeGlobs);
        this.excludeGlobs = copy(excludeGlobs);
        this.maxDepth = maxDepth;
        this.maxSizeBytes = maxSizeBytes;
    }

    public PolicyReason evaluate(BookmarkUri uri, long sizeBytes) {
        if (!uri.toString().startsWith(scopeRoot.toString()))
            return deny("OUT_OF_SCOPE", "outside scope root " + scopeRoot);
        if (maxDepth >= 0 && depthBelow(scopeRoot, uri) > maxDepth)
            return deny("MAX_DEPTH_EXCEEDED", "deeper than " + maxDepth);
        if (maxSizeBytes >= 0 && sizeBytes > maxSizeBytes)
            return deny("MAX_SIZE_EXCEEDED", sizeBytes + " > " + maxSizeBytes);
        String path = uri.path();
        if (matchesAny(path, excludeGlobs))
            return deny("EXCLUDED_BY_PATTERN", "exclude glob matched");
        if (!includeGlobs.isEmpty() && !matchesAny(path, includeGlobs))
            return deny("NOT_INCLUDED", "no include glob matched");
        return new PolicyReason(AcceptanceDecision.ACCEPT, "IN_SCOPE");
    }

    private static PolicyReason deny(String code, String detail) {
        return new PolicyReason(AcceptanceDecision.DENY, code + ": " + detail);
    }
    // depthBelow/matchesAny/copy: private Helfer, Glob via PathMatcher("glob:...")
}
```

*(**Verifiziert:** `PolicyReason` hat nur den Konstruktor `(AcceptanceDecision, String reason)` — keine Fabriken, kein Code-Feld. Der ReasonCode wird daher als Präfix im Reason-String transportiert (`"MAX_DEPTH_EXCEEDED: …"`). Verbesserungsvorschlag für den PR: `PolicyReason` additiv um ein optionales `code`-Feld erweitern, damit Codes nicht per String-Parsing ausgewertet werden müssen — abwärtskompatibel, da bestehende Aufrufer den Zwei-Argument-Konstruktor behalten.)*

#### `CacheInvalidationPolicy`

Erklärt: entscheidet, ob ein Bronze-Stand *ohne* Upstream-Kontakt weiterverwendet werden darf. Zeitbasiert als erste Implementierung (`maxAgeMillis` seit `recordedAtMillis` der aktuellen Version); Ergebnis dreiwertig, damit der Aufrufer zwischen „nutzen", „revalidieren (Metadata-Check)" und „verwerfen" unterscheiden kann.

```java
package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

public interface CacheInvalidationPolicy {
    enum CacheDecision { USE_CACHED, REVALIDATE, INVALIDATE }
    CacheDecision evaluate(long versionRecordedAtMillis, long nowMillis);
}
```

```java
package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

public final class MaxAgeCacheInvalidation implements CacheInvalidationPolicy {
    private final long revalidateAfterMillis; private final long invalidateAfterMillis;
    public MaxAgeCacheInvalidation(long revalidateAfterMillis, long invalidateAfterMillis) {
        if (revalidateAfterMillis > invalidateAfterMillis)
            throw new IllegalArgumentException("revalidate must be <= invalidate");
        this.revalidateAfterMillis = revalidateAfterMillis;
        this.invalidateAfterMillis = invalidateAfterMillis;
    }
    @Override public CacheDecision evaluate(long recordedAt, long now) {
        long age = now - recordedAt;
        if (age >= invalidateAfterMillis) return CacheDecision.INVALIDATE;
        if (age >= revalidateAfterMillis) return CacheDecision.REVALIDATE;
        return CacheDecision.USE_CACHED;
    }
}
```

### Tests

Digest-Strategie: first/changed/unchanged/observed-null; Metadata-Strategie: mtime- oder size-Änderung reicht; Scope: out-of-scope, depth-Grenzfall (== maxDepth erlaubt), size-Grenzfall, exclude schlägt include, leere include-Liste = alles erlaubt; Invalidation: Grenzwerte exakt (age == revalidate → REVALIDATE), Konstruktor-Validierung. Alle ohne Repository-/IO-Abhängigkeit.

### Out of scope / do-not-copy

Kein `scheduleMode`/Timer, kein `securityMode`, keine SourceType-Enums (FTP/NDV/Mail) in tamias, kein Archiv-Zugriff aus tamias heraus, keine Persistenz von Policies (Konfiguration kommt später über `ConfigSnapshot`-Follow-up — siehe „Vergessenes", Kapitel 18).

### Akzeptanzkriterien

Java 8; jede Policy einzeln getestet; ReasonCodes maschinenlesbar; ArchUnit „TAMIAS_MUST_STAY_POLICY_STEWARD" grün; Überlappung mit `ResourcePolicy` im PR dokumentiert; Inventar-Zeile aktualisiert.

---

# Kapitel 6: Deigma: Schwer-Extraktoren (#42)


**Modul:** `proasteion:emporion:deigma` (neue Submodule) · **Java 8** · **Vor/parallel zu #36**

### Designentscheidungen

1. **Ein Gradle-Submodul pro schwerer Bibliothek**, nicht ein Sammelmodul: `deigma-pdf` (PDFBox), `deigma-office` (POI: DOCX/XLSX), `deigma-html` (JSoup), `deigma-records` (RecordStructureCodec, dependency-frei). Grund: Wer nur `file:`+Markdown braucht, zieht keine 30-MB-Abhängigkeiten; `chatgpt-build.sh` (dependency-freier Smoke-Compile) bleibt funktionsfähig, weil der deigma-Kern unberührt bleibt.
2. **Registrierung über die bestehende `ExtractionRegistry`**, keine neuen Kerntypen. Jeder Extraktor implementiert das vorhandene `ResourceExtractor`-Interface (`supports(DetectedContentType)`, `extract(ExtractionRequest)`). Der Kompositionspunkt (`proasteion:application`, aus #10) registriert, was auf dem Classpath ist — per expliziter Registrierung, kein ServiceLoader-Magie im ersten Wurf.
3. **Fehler sind Ergebnisse, keine Exceptions:** korruptes PDF → `ExtractionResult`-Failure mit Warnung, konsistent zum bestehenden deigma-Muster („Explicit success/failure result", deigma-Inventar). Ein kaputtes Dokument darf nie einen Run abbrechen.
4. **Kein Tika im ersten PR.** Tika zieht einen großen transitiven Baum und überlappt mit `SimpleContentDetector`. Erst wenn reale Inhalte an den vier Extraktoren vorbeilaufen, als eigenes `deigma-tika`-Fallback-Modul nachrüsten (Registry-Priorität: spezifisch vor Fallback — Registrierungsreihenfolge nutzt das bestehende Prioritätsverhalten).
5. **`RecordStructureCodec` gehört hierher, nicht in holkas:** Fixe Satzlängen/Record-Formate sind Content-Struktur, nicht Transport (MVS-Transfer liefert Bytes; deren *Deutung* ist deigma). Das präzisiert die im deigma-Inventar offene Zuordnungsfrage.

### ⚠️ Problem gefunden

`ExtractionRequest` transportiert Content vermutlich als `byte[]` im Speicher. Für PDFs/XLSX im zweistelligen MB-Bereich ok, aber PST-Attachments (#36) können groß werden. **Kein Umbau jetzt** (YAGNI), aber im PR einen Grenzwert dokumentieren (Vorschlag: Extraktoren lehnen > 64 MB mit Failure-Reason `CONTENT_TOO_LARGE` ab) — die Scope-Policy aus #5 (`maxSizeBytes`) greift ohnehin davor.

### Klassen (je Modul ein Extraktor; Muster identisch)

#### `PdfTextExtractor` (`deigma-pdf`, Abhängigkeit `org.apache.pdfbox:pdfbox:2.0.x` — Java-8-kompatibel; 3.x liefe zwar ebenfalls auf Java 8, ändert aber die Lade-API (`Loader.loadPDF`) und bringt weniger Feldreife für den hiesigen Zweck; Wahl im PR so begründen)

Erklärt: seitenweise Extraktion in einen Block pro Seite (`BlockKind.PARAGRAPH` mit Seiten-Metadatum) — das erhält Positionsbezug für spätere Zitate, ohne ein Layoutmodell zu bauen. Verschlüsselte PDFs ohne Passwort → Failure, kein Prompt (Credentials wären adyton-Territorium und sind für Dokument-Passwörter bewusst NICHT vorgesehen — als offene Designfrage im PR notieren).

```java
package com.aresstack.corenth.proasteion.emporion.deigma.pdf;

import com.aresstack.corenth.proasteion.emporion.deigma.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

public final class PdfTextExtractor implements ResourceExtractor {

    private static final long MAX_BYTES = 64L * 1024 * 1024;

    @Override public boolean supports(DetectedContentType contentType) {
        return "application/pdf".equals(contentType.mimeType());
    }

    @Override public ExtractionResult extract(ExtractionRequest request) {
        byte[] bytes = request.content();
        if (bytes.length > MAX_BYTES)
            return ExtractionResult.failure(request.ref(), "CONTENT_TOO_LARGE: " + bytes.length);
        PDDocument doc = null;
        try {
            doc = PDDocument.load(new ByteArrayInputStream(bytes));
            if (doc.isEncrypted())
                return ExtractionResult.failure(request.ref(), "PDF_ENCRYPTED");
            PDFTextStripper stripper = new PDFTextStripper();
            List<ExtractedBlock> blocks = new ArrayList<ExtractedBlock>();
            int pages = doc.getNumberOfPages();
            for (int p = 1; p <= pages; p++) {
                stripper.setStartPage(p); stripper.setEndPage(p);
                String text = stripper.getText(doc).trim();
                if (!text.isEmpty())
                    blocks.add(ExtractedBlock.paragraph(text).withMetadata("page", String.valueOf(p)));
            }
            return ExtractionResult.success(ExtractedDocument.of(request.ref(), blocks));
        } catch (Exception e) {
            return ExtractionResult.failure(request.ref(), "PDF_PARSE_FAILED: " + e.getMessage());
        } finally {
            if (doc != null) try { doc.close(); } catch (Exception ignore) {}
        }
    }
}
```

*(Signaturen `ExtractionResult.failure/success`, `ExtractedBlock.paragraph`, `withMetadata` im PR gegen den Bestand abgleichen; falls `withMetadata` fehlt, Metadaten-Map am Block ergänzen — kleiner, abwärtskompatibler Kern-Change, im PR begründen.)*

#### `DocxTextExtractor` / `XlsxTextExtractor` (`deigma-office`, POI 5.2.x — Java-8-kompatibel; 4.1.x nur falls transitiv schlanker gewünscht, im PR entscheiden)

Erklärt: DOCX → Absätze als Blocks, Überschriften-Styles auf `BlockKind.HEADING` gemappt (füttert das Markdown-Heading-Kontextverhalten des Chunkers); XLSX → pro Sheet ein Block mit tab-separierten Zeilen, Zellwerte via `DataFormatter` (keine Roh-Serials). Formeln als Wert, nicht als Formeltext.

#### `HtmlTextExtractor` (`deigma-html`, JSoup 1.15.x)

Erklärt: `title` → Dokumenttitel, `h1..h6` → HEADING-Blocks, `p/li/pre` → PARAGRAPH/CODE; `script/style/nav` entfernt. Kein Rendering, keine Links-Auflösung.

#### `RecordStructureExtractor` (`deigma-records`, dependency-frei)

Erklärt: adaptiert MainframeMates `RecordStructureCodec`-Konzept — feste Satzlänge, Trailing-Padding-Trim, EBCDIC/ASCII-Charset-Parameter. Konfiguration kommt als Hint im `ExtractionRequest` (`record.length`, `record.charset`), gesetzt vom MVS-Connector-Pfad.

### Tests

Je Extraktor: Happy Path mit im Test generiertem Minimaldokument (PDFBox/POI können die Fixtures selbst erzeugen — keine Binär-Fixtures einchecken), korrupte Bytes → Failure statt Exception, `supports()`-Abgrenzung, Registry-Integration (spezifischer Extraktor gewinnt vor künftigem Fallback), Blockreihenfolge stabil. `deigma-records`: Padding-Trim, Restbytes < Satzlänge → Warnung.

### Out of scope / do-not-copy

Kein Tika, kein OCR, kein Rendering/Preview (Swing-Renderer bleiben do-not-copy), keine Einbettung in den Kern-`deigma`-Classpath, kein Excel-*Import* (das ist der Plugin-Fall aus dem deigma-Inventar).

### Akzeptanzkriterien

Java 8; vier neue Submodule mit isolierten Abhängigkeiten; deigma-Kern ohne neue Dependencies; `chatgpt-build.sh` weiterhin lauffähig; ArchUnit `DEIGMA_MUST_STAY_SHALLOW_EXTRACTION` grün; deigma-Inventar-Zeilen von „deferred" auf ✅.

---

# Kapitel 7: Holkas/Mail: PST/OST-Connector (#36)


**Modul:** `proasteion:emporion:holkas` (Submodul `holkas-mail`) · **Java 8** · **Nach #10-Slice-1, mit #42**

### Designentscheidungen

1. **Explizit ohne Auth-Pfad** (Issue-Vorgabe): PST/OST sind lokale Dateien im Nutzerkontext. Der Connector ähnelt `FileSystemResourceConnector`, nicht dem FTP-Muster. Keine adyton-Imports — das gehört als Negativ-Test in den PR.
2. **Bibliothek: `com.pff:java-libpst:0.9.x`** (reines Java, Apache-Lizenz, Java-8-tauglich) in einem isolierten Submodul `holkas-mail` — gleiche Isolationslogik wie #42.
3. **URI-Schema `mail:`** mit Locator `mail:<pst-pfad>!/<folder-pfad>/<message-id>`. Der PST-Dateipfad ist Teil der URI (mehrere Archive gleichzeitig adressierbar); `!`-Trennung analog JAR-URLs, dokumentiert in `BookmarkUri`-Javadoc. `list()` auf Archiv-Ebene liefert Ordner, auf Ordner-Ebene Messages.
4. **Eine Message wird als RFC-822-ähnlicher Text materialisiert** (Header-Auszug + Body); Attachments werden als *eigene* Kind-Ressourcen gelistet (`mail:...!/.../msg-42/att-1`) und einzeln gefetcht — so läuft jedes Attachment einzeln durch tamias-Policy (Size!) und deigma (#42-Extraktoren), statt eine Mail als Monolith zu behandeln.
5. **Nur Lesen.** Kein Verändern/Löschen im Store; `VirtualResourceKind.MESSAGE` aus dem astu-Modell wird endlich real genutzt.

### ⚠️ Konflikt / Hinweis

Der Connector ist der erste, der **hierarchisches Listing über mehrere Ebenen** (Archiv → Ordner → Message → Attachment) braucht. **Verifiziert:** `ResourceListingEntry` trägt bereits `VirtualResourceKind` (und `MESSAGE`/`DIRECTORY` existieren im Kind-Enum) — aber **kein `hasChildren`**. Ergänzung als SPI-Zusatz mit Default `false` ist abwärtskompatibel und wird in diesem PR gemacht; Konsumenten (Exedra-Baum später, #10-Traversierung) können damit Ordner von Blättern unterscheiden, ohne pro Eintrag ein Probe-Listing zu machen. Zweitens: OST-Dateien sind bei laufendem Outlook gesperrt — Fetch-Fehler als `ResourceConnectorException` mit klarer Meldung, kein Retry-Loop.

### Klassen

#### `MailLocator`

Erklärt: Parsen/Formatieren des `mail:`-Locators an einer Stelle, analog `MvsDatasetLocator`. Kein Regex-Streusand in Connector-Methoden.

```java
package com.aresstack.corenth.proasteion.emporion.holkas.mail;

public final class MailLocator {
    private final String storePath;   // filesystem path to .pst/.ost
    private final String folderPath;  // "" = store root, "Inbox/Sub" otherwise
    private final String messageId;   // null = folder level
    private final String attachmentId; // null = message level

    private MailLocator(String storePath, String folderPath, String messageId, String attachmentId) {
        this.storePath = storePath; this.folderPath = folderPath;
        this.messageId = messageId; this.attachmentId = attachmentId;
    }

    public static MailLocator parse(String rawSchemeSpecific) {
        int bang = rawSchemeSpecific.indexOf('!');
        if (bang < 0) return new MailLocator(rawSchemeSpecific, "", null, null);
        String store = rawSchemeSpecific.substring(0, bang);
        String rest = trimSlashes(rawSchemeSpecific.substring(bang + 1));
        // rest: folder[/msg-<id>[/att-<n>]]
        String folder = rest; String msg = null; String att = null;
        int mi = rest.indexOf("/msg-");
        if (mi >= 0) {
            folder = rest.substring(0, mi);
            String tail = rest.substring(mi + 1);
            int ai = tail.indexOf("/att-");
            if (ai >= 0) { msg = tail.substring(4, ai); att = tail.substring(ai + 5); }
            else { msg = tail.substring(4); }
        }
        return new MailLocator(store, folder, msg, att);
    }

    public String storePath() { return storePath; }
    public String folderPath() { return folderPath; }
    public String messageId() { return messageId; }
    public String attachmentId() { return attachmentId; }
    public boolean isStore() { return folderPath.isEmpty() && messageId == null; }
    public boolean isMessage() { return messageId != null && attachmentId == null; }
    public boolean isAttachment() { return attachmentId != null; }

    private static String trimSlashes(String s) {
        int a = 0, b = s.length();
        while (a < b && s.charAt(a) == '/') a++;
        while (b > a && s.charAt(b - 1) == '/') b--;
        return s.substring(a, b);
    }
}
```

#### `PstResourceConnector`

Erklärt: implementiert das bestehende `ResourceConnector`-SPI. `PSTFile` wird pro Fetch geöffnet und geschlossen (java-libpst hält File-Handles); ein Session-Cache wäre verfrüht — falls Messungen ihn später rechtfertigen, gehört er hinter das #33/#5-Cache-Modell, nicht in den Connector (Lehre aus MainframeMate).

```java
package com.aresstack.corenth.proasteion.emporion.holkas.mail;

import com.aresstack.corenth.astu.*;
import com.aresstack.corenth.proasteion.emporion.holkas.*;
import com.pff.*;
import java.io.IOException;

public final class PstResourceConnector implements ResourceConnector {

    @Override public ResourceScheme supportedScheme() { return ResourceScheme.of("mail"); }

    @Override public ResourceListing list(VirtualResourceRef ref) throws IOException {
        MailLocator loc = MailLocator.parse(ref.uri().schemeSpecificPart());
        try {
            PSTFile pst = new PSTFile(loc.storePath());
            try {
                PSTFolder folder = resolveFolder(pst, loc.folderPath());
                if (loc.isMessage()) return listAttachments(ref, folder, loc);
                return loc.messageId() == null
                        ? listFoldersAndMessages(ref, folder)
                        : ResourceListing.empty(ref);
            } finally { pst.getFileHandle().close(); }
        } catch (PSTException e) {
            throw new IOException("PST structure error: " + e.getMessage(), e);
        }
    }

    @Override public RawResource fetch(VirtualResourceRef ref) throws IOException {
        MailLocator loc = MailLocator.parse(ref.uri().schemeSpecificPart());
        try {
            PSTFile pst = new PSTFile(loc.storePath());
            try {
                PSTMessage msg = resolveMessage(pst, loc);
                if (loc.isAttachment()) {
                    PSTAttachment att = msg.getAttachment(Integer.parseInt(loc.attachmentId()));
                    return rawResource(ref, readFully(att.getFileInputStream()),
                            att.getLongFilename(), att.getMimeTag());
                }
                String text = MailMessageFormatter.toPlainText(msg); // headers + body
                return rawResource(ref, text.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        subjectOf(msg), "message/rfc822");
            } finally { pst.getFileHandle().close(); }
        } catch (PSTException e) {
            throw new IOException("PST read error: " + e.getMessage(), e);
        }
    }
    // resolveFolder / resolveMessage / listFoldersAndMessages / listAttachments / readFully /
    // rawResource / subjectOf: private Helfer
}
```

*(**Verifiziert:** `RawResource` hat keine `binary/text`-Fabriken, sondern Konstruktoren über `RawResourceContent` + `RawResourceMetadata`, und `ResourceListing` wird direkt mit `(containerRef, entries, …)` konstruiert — ein leeres Listing ist `new ResourceListing(ref, Collections.<ResourceListingEntry>emptyList(), …)`. Der private Helfer `rawResource(...)` kapselt den Konstruktoraufbau an einer Stelle. Zusätzlich: `ResourceScheme` kennt bereits Konstanten für FILE/FTP/NDV — im PR analog eine `MAIL`-Konstante ergänzen statt überall `ResourceScheme.of("mail")` zu streuen. `MailMessageFormatter` als eigene kleine Klasse: From/To/Date/Subject-Header, dann Body — deterministisch für Digest-Stabilität: **kein** Zeitstempel des Abrufs in den Text!)*

### Tests

Locator-Parsing (Store/Folder/Message/Attachment, Kanten: `!` fehlt, Slashes), Formatter-Determinismus (zweimal formatieren = identische Bytes → stabiler Digest, kritisch für #33-UNCHANGED), Connector gegen eine im Test per java-libpst *lesbare* Mini-PST-Fixture (einchecken, <50 KB, selbst erzeugt), gesperrte Datei → IOException mit klarer Meldung, ArchUnit: keine adyton-Imports in `holkas-mail`.

### Out of scope / do-not-copy

Kein IMAP/Exchange/Graph (das wäre ein Auth-Connector — eigenes Issue bei Bedarf), kein Schreiben, kein MainframeMate-`MailSourceScanner`-Kopieren (dessen Scan-Logik ist jetzt Harbor/Lifecycle), keine Attachment-Extraktion im Connector (deigma!).

### Akzeptanzkriterien

Java 8; isoliertes Submodul; `mail:`-Listing zweistufig; Attachments als eigene Ressourcen; Digest-stabile Message-Texte; Ende-zu-Ende-Test über den #10-Lifecycle (Mail → Policy → Bronze → Index → Suche) als Integrationstest im `application`-Modul.

---

# Kapitel 8: Holkas/NDV: Connector mit Session-Auth (#34)


**Module:** `proasteion:emporion:holkas` (Submodul `holkas-ndv`) + `proasteion:platform:security-*` (Strategie) · **Java 8** · **Nach #10-Slice-1; Vorlage: FTP/MVS-Slice**

### Designentscheidungen

1. **`research/ndv` (172 Dateien) wird nicht portiert, sondern als Protokoll-Referenz gelesen.** Das dortige PAL-Protokoll (`PalTypeConnect`, `PalTypeLibId`, `PalTypeSystemFile`, `NdvSessionContext`) ist die einzige Dokumentation des NDV-Wire-Formats — aber es ist mit dem Passwort-Durchreich-Antipattern verwoben (`sysFile.getPassword()` pro Request, Auth-Analyse §2/§3). Reimplementiert wird nur die schmale Teilmenge: connect, library-list, member-list, member-read. Editor-/Debugger-Funktionen des NDV-Protokolls sind out of scope.
2. **Session-Kapselung nach dem FTP-Muster:** `NdvClientSession` (rohes Protokoll, paketprivat), `NdvSessionFactory` (öffnet Session — konsumiert dabei einmalig Credentials), `NdvAccessHandle implements AccessHandle` (hält Session, gibt nie Credentials heraus), `NdvAuthenticationStrategy` in `platform` (einzige Stelle, die `SecretMaterial` sieht). Damit verschwindet das zentrale MainframeMate-Problem: Das Passwort existiert nur innerhalb `authenticate(...)` und wird für Folge-Requests **nicht** erneut gebraucht — die Session ist der Zustand, nicht das Passwort.
3. **Das PAL-„Passwort pro Library-Request"-Verhalten wird sessionseitig gelöst:** Falls das Protokoll je Request Re-Auth verlangt (so wirkt `NdvSessionContext.getPassword()` im Research-Code), hält die Session ein *vom Server ausgehandeltes Token/Kontext-Objekt* — niemals das Klartextpasswort. Sollte sich beim Implementieren zeigen, dass der Server zwingend das Klartextpasswort pro PAL-Request will, ist das ein dokumentierpflichtiger Befund: Dann kapselt `NdvClientSession` das Passwort als `char[]` in einem final-Feld, das bei `close()` genullt wird, und der PR begründet die Ausnahme mit Verweis auf die ArchUnit-Secret-Regeln (die Session liegt dann im vertrauenswürdigen `platform`-Adapter, nicht in holkas!). **Diese Frage zuerst am Research-Code klären, bevor Code entsteht.**
4. **URI-Schema `ndv:`** mit Locator `ndv://<host>[:<port>]/<library>/<member>`; `list()` auf Host-Ebene = Libraries, auf Library-Ebene = Members. Locator-Klasse analog `MvsDatasetLocator`.
5. **Route-Planning wiederverwenden:** Der jüngste FTP-Slice hat `platform:network`-Routing („routed MVS session factory"). Die NDV-Factory nutzt dieselben Routing-Ports — kein zweites Proxy-Modell.

### ⚠️ Konflikt / Risiko

Punkt 3 ist das Hauptrisiko des Issues: Wenn NDV-PAL klartextbasiertes Re-Auth erzwingt, kollidiert die reine Handle-Doktrin mit dem Protokoll. Die Auflösung (Secret-haltende Session im `platform`-Modul, holkas sieht nur das Handle) ist mit den bestehenden ArchUnit-Regeln vereinbar (`SECRET_..._TRUSTED_PLATFORM_ADAPTERS` erlaubt genau das), muss aber als bewusste Entscheidung im PR stehen. **Empfehlung:** Vor Implementierung einen kurzen Analyse-Kommentar im Issue mit dem Wire-Format-Befund aus `research/ndv/.../PalTypeConnect.java` u. a.

### Klassen (Skizze der Kernschnitte)

#### `NdvLocator` (`holkas-ndv`)

```java
package com.aresstack.corenth.proasteion.emporion.holkas.ndv;

public final class NdvLocator {
    private final String host; private final int port;
    private final String library; private final String member;

    public NdvLocator(String host, int port, String library, String member) {
        if (host == null || host.isEmpty()) throw new IllegalArgumentException("host required");
        this.host = host; this.port = port <= 0 ? 2700 : port;
        this.library = emptyToNull(library); this.member = emptyToNull(member);
    }
    public static NdvLocator parse(java.net.URI uri) {
        String[] seg = trim(uri.getPath()).split("/");
        return new NdvLocator(uri.getHost(), uri.getPort(),
                seg.length > 0 ? seg[0] : null, seg.length > 1 ? seg[1] : null);
    }
    public boolean isHostLevel() { return library == null; }
    public boolean isLibraryLevel() { return library != null && member == null; }
    public boolean isMemberLevel() { return member != null; }
    public String host() { return host; } public int port() { return port; }
    public String library() { return library; } public String member() { return member; }
    private static String emptyToNull(String s){ return s==null||s.isEmpty()?null:s; }
    private static String trim(String p){ return p==null?"":p.replaceAll("^/+|/+$",""); }
}
```

#### `NdvAccessHandle` (`platform:security-…` oder `holkas-ndv` je nach Punkt-3-Befund)

Erklärt: Spiegel des `FtpAccessHandle`-Musters — Grant + Session, `close()` schließt Session und macht das Handle unbrauchbar. Fachmethoden delegieren an die Session; **keine** Methode liefert Credentials oder die rohe Socket-Verbindung heraus.

```java
public final class NdvAccessHandle implements AccessHandle {
    private final AccessGrant grant;
    private final NdvClientSession session;
    private volatile boolean closed;

    public NdvAccessHandle(AccessGrant grant, NdvClientSession session) {
        if (grant == null || session == null) throw new IllegalArgumentException("grant/session required");
        this.grant = grant; this.session = session;
    }
    @Override public AccessGrant grant() { return grant; }

    public java.util.List<String> listLibraries() throws java.io.IOException { ensureOpen(); return session.listLibraries(); }
    public java.util.List<String> listMembers(String library) throws java.io.IOException { ensureOpen(); return session.listMembers(library); }
    public byte[] readMember(String library, String member) throws java.io.IOException { ensureOpen(); return session.readMember(library, member); }

    @Override public void close() { closed = true; session.close(); }
    private void ensureOpen() { if (closed) throw new IllegalStateException("handle closed"); }
}
```

#### `NdvAuthenticationStrategy` (`platform`)

Erklärt: einzige `SecretMaterial`-Konsumentin; spiegelt `MvsFtpAuthenticationStrategy`. **Verifiziert:** `AuthenticationMethod` ist kein Enum, sondern eine finale Klasse mit statischen Konstanten — und `NDV_PASSWORD` **existiert bereits** (ebenso `ResourceScheme.NDV` in astu). Es ist nichts zu ergänzen; `supports()` vergleicht per `equals` wie im FTP-Bestand.

```java
public final class NdvAuthenticationStrategy implements AuthenticationStrategy<NdvAccessHandle> {
    private final NdvSessionFactory sessionFactory;
    public NdvAuthenticationStrategy(NdvSessionFactory sessionFactory) { this.sessionFactory = sessionFactory; }

    @Override public boolean supports(AuthenticationMethod method) {
        return AuthenticationMethod.NDV_PASSWORD.equals(method);
    }
    @Override public NdvAccessHandle authenticate(AccessRequest request, SecretMaterial material)
            throws AccessException {
        try {
            NdvClientSession session = sessionFactory.connect(
                    request.targetSystem(), request.principal(), material); // material consumed here only
            // Grant-Erzeugung nach dem verifizierten MvsFtp-Muster (kein GrantFactory im Bestand):
            AccessGrant grant = new AccessGrant(
                    "ndv-" + System.nanoTime(),
                    request.targetSystem(),
                    material.principal(),
                    request.purpose(),
                    request.scope(),
                    System.currentTimeMillis() + request.requestedTtlMillis());
            return new NdvAccessHandle(grant, session);
        } catch (java.io.IOException e) {
            throw new AccessException("NDV connect failed: " + e.getMessage(), e);
        }
    }
}
```

#### `NdvResourceConnector` (`holkas-ndv`)

Erklärt: implementiert `ResourceConnector`; beschafft das Handle über den `AccessBroker` (Konstruktor-injiziert, wie beim FTP-Connector) und mappt Libraries/Members auf `ResourceListing`/`RawResource`. `VirtualResourceKind.DATASET` für Libraries, `FILE` für Members.

### Tests

Locator-Parsing inkl. Default-Port und Kantenfälle; Handle: `close()` → `IllegalStateException` bei Folgezugriff; Strategy: `supports`-Abgrenzung, IOException → AccessException; Connector gegen eine Fake-`NdvClientSession` (Interface extrahieren, keine echten Sockets im Unit-Test); Wire-Format-Tests der Session gegen aufgezeichnete PAL-Byte-Fixtures aus dem Research-Code (die dortigen Tests als Quelle); ArchUnit: `SecretMaterial` nur im platform-Teil.

### Out of scope / do-not-copy

Kein Editor-/Debug-Protokollteil, kein `NdvSessionContext`-Port, kein `LoginManager`, kein Swing (`ConnectNdvMenuCommand` ist UI-Territorium für später), kein Passwort in holkas-Typen, kein eigener Retry/Block-Mechanismus (kommt ggf. mit #43-Prompt-Flow).

### Akzeptanzkriterien

Java 8; vorhandene Konstanten `AuthenticationMethod.NDV_PASSWORD` und `ResourceScheme.NDV` genutzt (nichts zu ergänzen); Passwortfluss ausschließlich Strategy→Session, per Test belegt; erster Ende-zu-Ende-Lauf `ndv:`-Member → Bronze → Index über den #10-Lifecycle (Integrationstest mit Fake-Session); Migrations-Inventar-Zeile `ndv` auf 🔧.

---

# Kapitel 9: Holkas/FTP: JES Submit & Spool (#35)


**Modul:** `proasteion:emporion:holkas` (Paket `ftp.jes`, kein neues Submodul) · **Java 8** · **Nach #34 begonnen werden kann parallel — harte Abhängigkeiten: der bestehende FTP/MVS-Slice (`FtpAccessHandle`, `FtpClientSession`-SPI) *und* ein noch nicht vorhandener produktiver FTP-Transport (Implementierung von `FtpClientSession`/`MvsFtpSessionAuthenticator`, eigener Slice — siehe Korrektur Session 0 unten); gegen Fakes lässt sich der JES-Code vorab schreiben, lauffähig gegen einen Host wird er erst mit dem Transport**

### Designentscheidungen

1. **JES ist kein Connector, sondern eine Fähigkeit des FTP-Handles.** Die Issue-Vorgabe („ausdrücklich kein zweiter Login-Pfad") wird strukturell erzwungen: Alle JES-Klassen nehmen ein `FtpAccessHandle` als Konstruktorargument und haben **keinen** Zugriff auf `AccessBroker`, `AuthenticationStrategy` oder adyton-Typen. Ein ArchUnit-Zusatztest fixiert das (`ftp.jes`-Paket darf `com.aresstack.corenth.adyton..` nicht importieren, außer transitiv über das Handle-Interface).
2. **JES-Modus als geführter Session-Zustandswechsel:** `SITE FILETYPE=JES` schaltet die FTP-Session um; danach sind normale Dateioperationen semantisch anders. Um Zustandsverwirrung zu vermeiden (MainframeMate öffnete deshalb pro Submit eine neue Verbindung — teuer und Prompt-treibend, Auth-Analyse §2), kapselt `JesMode` das Muster „umschalten → arbeiten → zurückschalten" als try/finally um eine Operation. Die Session bleibt wiederverwendbar.
3. **Spool-Lesen liefert Ressourcen, kein Sonder-API:** Ein Job-Spool wird als `jes://`-Sicht *nicht* eingeführt (kein neues Schema — YAGNI); stattdessen liefern `listJobs`/`readSpool` einfache Wertobjekte. Ob JES-Ausgaben später indexierbar sein sollen (als virtuelles Schema), ist eine bewusst offene Frage für die Praxis — im PR als Follow-up-Kandidat notieren, nicht vorbauen.
4. **MainframeMate-Konzepte übernehmen, Zuschnitt neu:** `JesFtpJobSubmitter`-Ablauf (submit via STOR, Job-ID aus Reply parsen), `JesFtpService`-Spool-Listing. Nicht übernehmen: eigene Credential-Auflösung, Verbindungsaufbau pro Call, System.out-Logging.

### Klassen

#### `JesJobId`

Erklärt: Wertobjekt statt String — die Job-ID kommt aus dem 250er-Reply („It is known to JES as JOB12345") und ist die einzige Verknüpfung zwischen Submit und Spool; Parsing an genau einer Stelle.

```java
package com.aresstack.corenth.proasteion.emporion.holkas.ftp.jes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JesJobId {
    private static final Pattern REPLY = Pattern.compile("(JOB\\d{5,7}|J\\d{7}|TSU\\d{5,7}|STC\\d{5,7})");
    private final String value;

    private JesJobId(String value) { this.value = value; }

    public static JesJobId of(String raw) {
        if (raw == null || raw.isEmpty()) throw new IllegalArgumentException("job id required");
        return new JesJobId(raw);
    }
    /** Extracts the job id from an FTP submit reply line, or null if absent. */
    public static JesJobId fromSubmitReply(String replyText) {
        if (replyText == null) return null;
        Matcher m = REPLY.matcher(replyText);
        return m.find() ? new JesJobId(m.group(1)) : null;
    }
    public String value() { return value; }
    @Override public boolean equals(Object o){ return o instanceof JesJobId && value.equals(((JesJobId)o).value); }
    @Override public int hashCode(){ return value.hashCode(); }
    @Override public String toString(){ return value; }
}
```

#### `JesMode`

Erklärt: das try/finally-Muster für den Filetype-Wechsel. Statische Hilfsklasse mit funktionalem Callback (Java-8-Interface), damit kein Aufrufer den Rückschalt-Schritt vergessen kann.

```java
package com.aresstack.corenth.proasteion.emporion.holkas.ftp.jes;

import com.aresstack.corenth.proasteion.emporion.holkas.ftp.FtpClientSession;
import java.io.IOException;

final class JesMode {
    interface JesOperation<R> { R run(FtpClientSession session) throws IOException; }

    static <R> R within(FtpClientSession session, JesOperation<R> op) throws IOException {
        session.site("FILETYPE=JES");
        try {
            return op.run(session);
        } finally {
            try { session.site("FILETYPE=SEQ"); }
            catch (IOException restoreFailure) {
                // Session state is now unknown -> force close so the factory reopens cleanly.
                session.close();
            }
        }
    }
    private JesMode() {}
}
```

*(**Verifiziert:** `FtpClientSession` bietet heute nur `readBytes(MvsLocation, ResourceReadMode)`, `listNames(MvsLocation)` und `close()` — **weder `site(...)` noch eine Store-Methode.** Dieses Issue erweitert das Interface daher additiv um `site(String)` und `storeAndReadReply(String remoteName, byte[] content)`; eine Transport-Implementierung dahinter könnte beides trivial bedienen (Commons-Net: `sendSiteCommand`, `storeFile` + `getReplyString`). *Korrektur Session 0 (2026-10-06): Eine solche Implementierung existiert in keinem Corenth-Gradle-Modul. `FtpClientSession`, `FtpClientSessionFactory` und `MvsFtpSessionAuthenticator` haben außerhalb von Tests keine Implementierung, commons-net ist nur in `research/app` deklariert. Dieses Issue hat damit eine unausgesprochene Voraussetzung: einen echten FTP-Transport-Slice, der als eigenes Issue anzulegen ist.* Zu beachten: `readBytes`/`listNames` sind `MvsLocation`-typisiert — das Submit-Ziel ist aber kein Dataset, daher die String-basierte Store-Signatur. Zweite additive Ergänzung: `FtpAccessHandle.withSession(SessionOperation)` als kontrollierter Zugriffspunkt, damit JES-Code die Session nutzen kann, ohne dass das Handle sie als Getter herausgibt.)*

#### `JesJobSubmitter` / `JesSpoolReader`

Erklärt: die zwei fachlichen Einstiege. Submit streamt JCL-Text als STOR und parst die Job-ID; Spool-Reader listet Jobs (LIST im JES-Modus) und liest eine Spool-Datei (RETR `<jobid>.<n>` bzw. `<jobid>` für alles — Dialekt aus MainframeMate übernehmen). Beide sind zustandslos bis auf das Handle.

```java
package com.aresstack.corenth.proasteion.emporion.holkas.ftp.jes;

import com.aresstack.corenth.proasteion.emporion.holkas.ftp.FtpAccessHandle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class JesJobSubmitter {
    private final FtpAccessHandle handle;
    public JesJobSubmitter(FtpAccessHandle handle) {
        if (handle == null) throw new IllegalArgumentException("handle required");
        this.handle = handle;
    }

    /** Submits JCL text; returns the JES job id. */
    public JesJobId submit(final String jclText) throws IOException {
        if (jclText == null || jclText.trim().isEmpty())
            throw new IllegalArgumentException("jclText must not be empty");
        return handle.withSession(new FtpAccessHandle.SessionOperation<JesJobId>() {
            @Override public JesJobId run(com.aresstack.corenth.proasteion.emporion.holkas.ftp.FtpClientSession s)
                    throws IOException {
                return JesMode.within(s, new JesMode.JesOperation<JesJobId>() {
                    @Override public JesJobId run(com.aresstack.corenth.proasteion.emporion.holkas.ftp.FtpClientSession session)
                            throws IOException {
                        String reply = session.storeAndReadReply("CORENTH.SUBMIT",
                                jclText.getBytes(StandardCharsets.US_ASCII));
                        JesJobId id = JesJobId.fromSubmitReply(reply);
                        if (id == null) throw new IOException("submit accepted but no job id in reply: " + reply);
                        return id;
                    }
                });
            }
        });
    }
}
```

*(`withSession`/`storeAndReadReply`: gegen die reale `FtpAccessHandle`-/Session-API abgleichen; das Handle hat laut Bestand Fixed-Session- und Factory-Modus — `withSession` als dünner Zugriffspunkt ist ggf. zu ergänzen und ersetzt direkten Session-Getter. `JesSpoolReader` analog: `List<JesJobSummary> listJobs(String ownerFilter)` und `byte[] readSpool(JesJobId id, int fileIndex)`.)*

### Tests

Job-ID-Parsing (JOB/J/TSU/STC-Varianten, kein Match → null); `JesMode` schaltet zurück auch bei Exception; Rückschalt-Fehler schließt Session; Submit gegen Fake-Session (Reply-Fixture aus MainframeMate-Testdaten), leeres JCL → IllegalArgument; Spool-Listing-Parsing gegen aufgezeichnete LIST-Ausgaben; ArchUnit-Zusatzregel „ftp.jes importiert kein adyton".

### Out of scope / do-not-copy

Kein eigenes Schema/Connector, kein Job-Monitoring/Polling-Scheduler, kein JCL-Generieren (propylaea-Thema), kein `JesFtpService`-Singleton, keine Credential-Berührung.

### Akzeptanzkriterien

Java 8; null neue adyton-Abhängigkeiten im JES-Paket (Test belegt); Submit+Spool über eine wiederverwendete Session (kein Reconnect pro Call — Test zählt Factory-Aufrufe); Reply-Dialekte aus dem Research-Code als Fixtures übernommen.

---

# Kapitel 10: Holkas/Wiki: MediaWiki-Connector (#37)


**Module:** `holkas-wiki` (Connector) + `platform` (Strategie) · **Java 8** · **Erster Cookie-/Token-basierter AccessHandle — Referenz für das „Derived Handle"-Muster der Auth-Analyse §4**

### Designentscheidungen

1. **JWBF wird nicht übernommen.** MainframeMate nutzte JWBF (`bot.login(user, new String(password))`) — die Bibliothek ist wartungsarm und erzwingt String-Passwörter. Der MediaWiki-Login (GET `meta=tokens` → POST `action=login` → Cookie) ist mit `HttpURLConnection` in ~100 Zeilen implementierbar; das hält das Modul dependency-frei und das Passwort als `char[]`-Lebensdauer minimal.
2. **Das Handle ist die Cookie-Session, nicht der Client:** `WikiAccessHandle` hält `CookieManager` + Basis-URL und stellt `get(apiParams)`-Aufrufe bereit. Nach `authenticate(...)` existiert das Passwort nirgends mehr — das ist der kanonische Fall aus der Auth-Analyse und sollte im Javadoc explizit als Muster-Referenz markiert werden.
3. **BotPassword-first:** Moderne MediaWiki-Instanzen verlangen für API-Login Bot-Passwörter (`user@botname`). Die Strategie behandelt den Principal transparent (enthält er `@`, ist es ein BotPassword-Login); klassischer Login bleibt möglich. Kein OAuth im ersten Wurf (Follow-up bei Bedarf).
4. **URI-Schema `wiki:` mit Site-Registry-Indirektion:** `wiki://<site-alias>/<Page_Title>`. Der Alias wird über eine kleine `WikiSiteRegistry` (alias → API-Basis-URL) aufgelöst, konfiguriert im Kompositionspunkt. Grund: Page-Titles enthalten Slashes/Sonderzeichen; die API-URL gehört nicht in jede Ressourcen-URI (Digest-Stabilität bei Site-Umzug).
5. **Content = Wikitext, nicht gerendertes HTML** (`action=query&prop=revisions&rvprop=content|timestamp`). Wikitext ist digest-stabil und chunker-freundlich (Überschriften-Syntax); Rendering wäre ein deigma-Thema. Der Revisions-Timestamp geht als Metadatum mit → tamias `METADATA`-ChangeDetection kann ohne Content-Fetch entscheiden (`prop=revisions&rvprop=timestamp` als billiger Probe-Call).

### ⚠️ Hinweis

`list()` auf Site-Ebene (= alle Seiten) kann riesig sein. Erste Implementierung: `list` liefert per `list=allpages` **seitenweise mit Fortsetzungs-Token**, gedeckelt auf ein konfigurierbares Maximum (Default 500), und dokumentiert die Grenze im Listing-Ergebnis. Vollständige Enumeration ist Sache des #10-Runs mit Scope-Policy (#5), nicht des Connectors.

### Klassen

#### `WikiAccessHandle` (`platform` oder `holkas-wiki` — Cookie ist kein Secret, daher holkas-wiki zulässig; im PR mit ArchUnit-Secret-Regeln abgleichen)

```java
package com.aresstack.corenth.proasteion.emporion.holkas.wiki;

import com.aresstack.corenth.adyton.AccessGrant;
import com.aresstack.corenth.adyton.AccessHandle;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;

public final class WikiAccessHandle implements AccessHandle {
    private final AccessGrant grant;
    private final String apiBaseUrl;
    private final CookieManager cookies;
    private volatile boolean closed;

    public WikiAccessHandle(AccessGrant grant, String apiBaseUrl, CookieManager cookies) {
        if (grant == null || apiBaseUrl == null || cookies == null)
            throw new IllegalArgumentException("grant/apiBaseUrl/cookies required");
        this.grant = grant; this.apiBaseUrl = apiBaseUrl; this.cookies = cookies;
    }

    @Override public AccessGrant grant() { return grant; }

    /** Executes an authenticated MediaWiki API GET; returns the raw JSON body. */
    public String apiGet(String queryString) throws IOException {
        if (closed) throw new IllegalStateException("handle closed");
        return WikiHttp.get(apiBaseUrl + "?format=json&" + queryString, cookies);
    }

    @Override public void close() {
        closed = true;
        cookies.getCookieStore().removeAll();
    }

    public static CookieManager newCookieManager() {
        return new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    }
}
```

#### `WikiAuthenticationStrategy`

Erklärt: Zwei-Schritt-Login; das Secret existiert nur im POST-Body-Aufbau und wird danach genullt. **Verifiziert:** `AuthenticationMethod.MEDIA_WIKI_LOGIN` existiert bereits in adyton (statische Konstante, kein Enum) — nichts zu ergänzen; Vergleich per `equals`.

```java
public final class WikiAuthenticationStrategy implements AuthenticationStrategy<WikiAccessHandle> {
    private final String apiBaseUrl;
    public WikiAuthenticationStrategy(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }

    @Override public boolean supports(AuthenticationMethod m) {
        return AuthenticationMethod.MEDIA_WIKI_LOGIN.equals(m);
    }

    @Override public WikiAccessHandle authenticate(AccessRequest request, SecretMaterial material)
            throws AccessException {
        java.net.CookieManager cookies = WikiAccessHandle.newCookieManager();
        char[] secret = null;
        try {
            String tokenJson = WikiHttp.get(apiBaseUrl
                    + "?format=json&action=query&meta=tokens&type=login", cookies);
            String loginToken = WikiJson.string(tokenJson, "query", "tokens", "logintoken");
            // Verifiziert: SecretMaterial bietet principal() und char[] secret().
            // Das Array gehoert dem Material (Broker verwaltet Lebensdauer via close()) —
            // deshalb hier NICHT nullen, nur lesen:
            secret = material.secret();
            String reply = WikiHttp.postForm(apiBaseUrl, cookies,
                    "format=json&action=login"
                    + "&lgname=" + WikiHttp.enc(request.principal())
                    + "&lgpassword=" + WikiHttp.enc(new String(secret))
                    + "&lgtoken=" + WikiHttp.enc(loginToken));
            if (!"Success".equals(WikiJson.string(reply, "login", "result")))
                throw new AccessException("MediaWiki login failed: "
                        + WikiJson.string(reply, "login", "result"));
            AccessGrant grant = new AccessGrant("wiki-" + System.nanoTime(),
                    request.targetSystem(), material.principal(), request.purpose(),
                    request.scope(), System.currentTimeMillis() + request.requestedTtlMillis());
            return new WikiAccessHandle(grant, apiBaseUrl, cookies);
        } catch (java.io.IOException e) {
            throw new AccessException("MediaWiki login I/O failure", e);
        }
    }
}
```

*(`new String(secret)` ist der unvermeidbare Rest — URL-Encoding braucht String; die Lebensdauer ist auf den Methodenscope begrenzt. Das `secret()`-Array wird bewusst nicht genullt, da es dem Material gehört (Broker schließt es). `WikiHttp`/`WikiJson`: kleine paketprivate Helfer — JSON-Zugriff per Minimal-Parser oder gezieltem String-Scan, keine Jackson-Abhängigkeit für drei Felder.)*

#### `WikiResourceConnector`

Erklärt: `fetch` = ein `apiGet` mit `prop=revisions&rvprop=content|timestamp&titles=<Page>`; Wikitext + Timestamp-Metadatum als `RawResource`. `list` = `list=allpages` mit Deckel (s. Hinweis). Handle-Beschaffung via `AccessBroker.acquire(...)` mit Wiederverwendung (der `acquire`-Pfad existiert im Broker genau für diesen Fall — langlebige Session, viele Reads).

### Tests

Strategy gegen einen eingebetteten `HttpServer` (`com.sun.net.httpserver`, JDK-Bordmittel): Token→Login-Sequenz, Failed-Result → AccessException, Cookie im Folgerequest vorhanden; kein Halten des `secret()`-Arrays über `authenticate` hinaus (Feld-Inspektion des Handles); Handle: `close()` leert CookieStore und sperrt `apiGet`; Connector: Title-Encoding (Leerzeichen/Umlaute/Slash), Listing-Deckel, Timestamp-Metadatum vorhanden; ArchUnit: Secret-Typen nur in der Strategie-Klasse.

### Out of scope / do-not-copy

Kein JWBF, kein Schreiben/Editieren, kein OAuth, kein HTML-Rendering, kein `WikiSourceScanner`-Port (Scan = #10-Run), keine Confluence-Vermischung (das ist #38 — anderes Auth-Modell).

### Akzeptanzkriterien

Java 8; dependency-freies Modul; vorhandene Konstante `MEDIA_WIKI_LOGIN` genutzt; Passwort-Lebensdauer auf `authenticate` begrenzt und getestet; Handle als dokumentierte Muster-Referenz für #38/#39 markiert; `wiki:`-Ende-zu-Ende gegen den Testserver durch den #10-Lifecycle.

---

# Kapitel 11: Holkas/Confluence: Basic & mTLS (#38)


**Module:** `holkas-confluence` (Connector) + `platform:security-…` (mTLS-Strategie) · **Java 8**

### Designentscheidungen

1. **Zwei getrennte Strategien, ein Handle-Typ.** Basic Auth erzeugt einen abgeleiteten Header, mTLS erzeugt einen `SSLContext` — beides mündet in dasselbe `ConfluenceAccessHandle`, das vorkonfigurierte `HttpURLConnection`s ausgibt. Damit bleibt der Connector strategie-agnostisch, und die Kombination (mTLS **und** Basic gleichzeitig, wie im Research-Code) ist Komposition statt Sonderfall.
2. **Der Header ist ein abgeleitetes Secret und bleibt im Handle privat.** MainframeMates Fehler war nicht der Header selbst, sondern dass `ConfluenceConnectionConfig` das Roh-Passwort per Getter trug. Corenth: Die Basic-Strategie baut `Authorization: Basic …` einmal in `authenticate(...)`; das Handle setzt ihn intern auf jede Connection — **kein** Getter für den Header-Wert (`toString` maskiert). Das ist strenger als „Header-Handle" aus der Analyse und kostet nichts.
3. **Windows-MY hinter einem Port:** `ClientKeyStoreProvider` (Interface) mit `WindowsMyKeyStoreProvider` (nur unter Windows funktionsfähig, `os.name`-Guard) und einer dateibasierten PKCS12-Implementierung für Tests/CI. Der private Schlüssel verlässt den Store nie — der `SSLContext` referenziert den `KeyManager`, exakt das gute Muster aus dem Research-Code, jetzt aber in einer Strategie statt ad hoc im REST-Client.
4. **Confluence-REST minimal:** `fetch` = `GET /rest/api/content/{id}?expand=body.storage,version`; Body ist Storage-XHTML → wird als `text/html`-Content geliefert und von #42-`deigma-html` extrahiert (bewusste Wiederverwendung statt eigenem XHTML-Parser). `list` = CQL-Suche `space=<KEY>` seitenweise mit Deckel (Muster aus #37 übernehmen). Version-Nummer als Metadatum → billige ChangeDetection.
5. **URI-Schema `confluence://<site-alias>/<content-id>`** — Content-ID statt Titel (stabil bei Umbenennung); Site-Alias-Registry wie in #37 (gemeinsame kleine `SiteRegistry`-Klasse erwägen, aber erst beim zweiten Nutzer extrahieren — YAGNI).

### ⚠️ Problem gefunden

**Verifiziert:** `AuthenticationMethod` bringt `HTTP_BASIC` und `MTLS_CERTIFICATE` **bereits mit** (finale Klasse mit Konstanten, kein Enum — es gibt kein `MTLS_WINDOWS_MY`; die vorhandene, generischere `MTLS_CERTIFICATE`-Konstante ist die richtige, der Windows-MY-Bezug steckt im `ClientKeyStoreProvider`). Das eigentliche Problem bleibt: Der Broker wählt *eine* Strategie pro Request. Für „mTLS + Basic" braucht der Request entweder eine Kompositions-Strategie (`ConfluenceCompositeStrategy`, die intern beide Schritte macht) oder zwei Broker-Durchläufe. **Entscheidung: Kompositions-Strategie** — ein Request, ein Grant, ein Handle; der Broker bleibt unverändert. Im PR als Designabschnitt dokumentieren.

### Klassen (Kernschnitte)

#### `ConfluenceAccessHandle`

```java
package com.aresstack.corenth.proasteion.emporion.holkas.confluence;

import com.aresstack.corenth.adyton.AccessGrant;
import com.aresstack.corenth.adyton.AccessHandle;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public final class ConfluenceAccessHandle implements AccessHandle {
    private final AccessGrant grant;
    private final String baseUrl;
    private final String authorizationHeader; // may be null (mTLS-only)
    private final SSLContext sslContext;      // may be null (basic-only)
    private volatile boolean closed;

    public ConfluenceAccessHandle(AccessGrant grant, String baseUrl,
                                  String authorizationHeader, SSLContext sslContext) {
        if (grant == null || baseUrl == null) throw new IllegalArgumentException("grant/baseUrl required");
        this.grant = grant; this.baseUrl = baseUrl;
        this.authorizationHeader = authorizationHeader; this.sslContext = sslContext;
    }

    @Override public AccessGrant grant() { return grant; }

    /** Opens a pre-authenticated connection for a REST path; caller reads and disconnects. */
    public HttpURLConnection open(String restPath) throws IOException {
        if (closed) throw new IllegalStateException("handle closed");
        HttpURLConnection c = (HttpURLConnection) new URL(baseUrl + restPath).openConnection();
        if (sslContext != null && c instanceof HttpsURLConnection)
            ((HttpsURLConnection) c).setSSLSocketFactory(sslContext.getSocketFactory());
        if (authorizationHeader != null)
            c.setRequestProperty("Authorization", authorizationHeader);
        c.setRequestProperty("Accept", "application/json");
        return c;
    }

    @Override public void close() { closed = true; }
    @Override public String toString() { return "ConfluenceAccessHandle{" + baseUrl + ", auth=***}"; }
}
```

#### `ConfluenceBasicAuthStrategy` / `WindowsMyMtlsStrategy` / `ConfluenceCompositeStrategy`

Erklärt: Basic-Strategie baut den Header aus `principal` + Secret (Secret-Scope wie in #37, `char[]` + `Arrays.fill`); mTLS-Strategie baut den `SSLContext` über `ClientKeyStoreProvider` — sie konsumiert **kein** `SecretMaterial` (der Key liegt im OS-Store), `authenticate` ignoriert das Material und dokumentiert das. Composite ruft beide und fügt zusammen.

```java
public interface ClientKeyStoreProvider {
    /** @return a KeyManager-ready KeyStore restricted to the given alias. */
    java.security.KeyStore load(String certificateAlias) throws java.security.GeneralSecurityException;
    char[] keyPassword(); // empty for Windows-MY (handled by MSCAPI)
}
```

#### `ConfluenceResourceConnector`

Erklärt: nutzt `handle.open(...)`; JSON-Zugriff wie in #37 minimal; `body.storage.value` → `RawResource` mit `text/html`-ContentType-Hint, `version.number` als Metadatum.

### Tests

Basic-Header korrekt kodiert (UTF-8-Credentials!), Secret genullt; Handle setzt Header/SSLFactory, `toString` maskiert, kein Header-Getter (Kompilierbarkeits-/API-Review); mTLS mit PKCS12-Testprovider gegen eingebetteten HTTPS-Testserver (selbstsigniertes Serverzertifikat, Client-Cert-Pflicht); Composite kombiniert; Windows-MY-Provider: reiner `os.name`-Guard-Test (skip auf Nicht-Windows, sichtbar); Connector: Version-Metadatum, Listing-Deckel; ArchUnit: Secret-Regeln.

### Out of scope / do-not-copy

Kein Confluence-Schreiben, kein Space-übergreifendes Crawling im Connector (Scope = #5/#10), kein Kopieren von `ConfluenceConnectionConfig` (Passwort-Getter!), kein PAT/OAuth im ersten Wurf (Follow-up-Kandidat, im PR notieren — Data Center vs. Cloud unterscheiden).

### Akzeptanzkriterien

Java 8; vorhandene Konstanten `HTTP_BASIC` + `MTLS_CERTIFICATE` genutzt (nichts zu ergänzen); Header nirgends abrufbar; mTLS-CI-Test läuft plattformunabhängig über PKCS12-Provider; Composite-Entscheidung dokumentiert.

---

# Kapitel 12: Holkas/SharePoint: SSO-first-Kaskade (#39)


**Module:** `holkas-sharepoint` + `platform` · **Java 8** · **Letzter der sechs Connectors — bewusst, da Windows-lastigster**

### Designentscheidungen

1. **Dreistufige Kaskade als explizite Strategie-Kette, nicht als if-Wald:** `SSO → gespeicherte Credentials → interaktiver Prompt (#43)`. Jede Stufe ist eine eigene `AuthenticationStrategy<SharePointAccessHandle>`; eine kleine `FallbackAuthenticationChain` probiert sie in Reihenfolge und unterscheidet dabei hart zwischen „Stufe nicht anwendbar" (weiter) und „Stufe fehlgeschlagen mit Nutzeraktion" (`AuthCancelledException` → Abbruch, kein Weiterfallen — sonst prompt-Loop wie in MainframeMate).
2. **SSO-Pfad = Java-Kerberos/NTLM über `Authenticator` + `HttpURLConnection`** gegen die SharePoint-REST-API (`/_api/web/...`), kein `net use` nötig, solange nur gelesen wird. Das ist die wichtigste Abweichung vom Research-Code: Der UNC-Mount (`net use`) war ein UI-/Explorer-Bedürfnis; für Indexierung reicht REST. **`net use` wird nur** für den Sonderfall UNC-only-Freigaben implementiert — als separate, klar markierte `UncMountFallback`-Klasse mit `ProcessRunner`-Port (testbar ohne Windows).
3. **Kein Passwort auf der Kommandozeile.** Wenn `net use` doch gebraucht wird: Passwort via stdin (`net use \\host\share /user:u *` fragt interaktiv — der ProcessRunner beantwortet den Prompt über den stdin-Stream). Die MainframeMate-Variante (Passwort als Argument, sichtbar in Prozessliste) ist explizit do-not-copy und als solche im PR-Text zu nennen.
4. **URI-Schema `sharepoint://<site-alias>/<server-relative-path>`;** `list` über `/_api/web/GetFolderByServerRelativeUrl('…')/Files,Folders`, `fetch` über `/$value`. `TimeLastModified` + `Length` als Metadaten → Metadata-ChangeDetection ohne Download.
5. **Handle-Formen:** SSO/Credential-Stufe liefern ein `SharePointAccessHandle` um vorkonfigurierte Connections (Muster #38); die UNC-Stufe liefert dasselbe Handle-Interface, intern aber pfadbasiert („mounted") — der Connector merkt den Unterschied nicht.

### ⚠️ Konflikt / Abhängigkeit

Die dritte Kaskadenstufe braucht den **#43-Prompt-Provider**. Reihenfolge daher: #39 implementiert Stufe 1+2 vollständig und definiert die Chain; Stufe 3 wird per Konstruktor-Injektion angeschlossen, sobald #43 gemergt ist (bis dahin: Chain endet mit klarer `SecretUnavailableException`). Das entkoppelt beide Issues sauber. Zweitens: NTLM über `Authenticator` ist JVM-global (`Authenticator.setDefault`) — Konflikt-Potenzial mit anderen Modulen; Lösung: gezielter `Authenticator` nur im SSO-Strategie-Scope setzen und im PR als bekannte JVM-Einschränkung dokumentieren (JDK bietet nichts Besseres in Java 8).

### Klassen (Kernschnitt)

#### `FallbackAuthenticationChain`

```java
package com.aresstack.corenth.proasteion.emporion.holkas.sharepoint;

import com.aresstack.corenth.adyton.*;
import java.util.List;

public final class FallbackAuthenticationChain implements AuthenticationStrategy<SharePointAccessHandle> {
    private final List<AuthenticationStrategy<SharePointAccessHandle>> chain;

    public FallbackAuthenticationChain(List<AuthenticationStrategy<SharePointAccessHandle>> chain) {
        if (chain == null || chain.isEmpty()) throw new IllegalArgumentException("chain must not be empty");
        this.chain = chain;
    }

    @Override public boolean supports(AuthenticationMethod method) {
        return AuthenticationMethod.SSO.equals(method)
                || AuthenticationMethod.SMB_NET_USE.equals(method);
    }

    @Override public SharePointAccessHandle authenticate(AccessRequest request, SecretMaterial material)
            throws AccessException {
        AccessException last = null;
        for (AuthenticationStrategy<SharePointAccessHandle> s : chain) {
            try {
                return s.authenticate(request, material);
            } catch (StageNotApplicableException notApplicable) {
                continue; // e.g. SSO unavailable outside domain -> try next stage
            } catch (AccessException failed) {
                last = failed;
                // Hard failure of an applicable stage: do not silently continue for
                // credential stages (avoids credential spraying); SSO hard-failure may continue.
                if (!(s instanceof SsoAuthenticationStrategy)) throw failed;
            }
        }
        throw last != null ? last : new AccessException("no authentication stage applicable");
    }
}
```

*(`StageNotApplicableException extends AccessException` als Marker; `AuthCancelledException` propagiert von selbst — sie ist checked und wird nicht gefangen. **Verifiziert:** `AuthenticationMethod.SSO` und `SMB_NET_USE` existieren bereits als Konstanten in adyton — nichts zu ergänzen; Vergleiche per `equals` statt `==` schreiben, da die Klasse einen öffentlichen `of(String)`-Weg für gleichnamige Instanzen bietet.)*

### Tests

Chain: not-applicable → nächste Stufe; harte Credential-Failure bricht ab; Cancelled propagiert ungefangen; SSO-Strategie gegen Testserver mit 401→Negotiate-Simulation (soweit ohne echtes Kerberos möglich: Verhalten bei fehlendem Ticket = StageNotApplicable); UNC-Fallback: ProcessRunner-Fake prüft, dass Passwort **nicht** in der Argumentliste erscheint (Kerntest!); Metadaten-Mapping; ArchUnit.

### Out of scope / do-not-copy

Kein Schreiben, kein Graph-API/OAuth (Cloud-SharePoint = eigenes Follow-up), kein Swing-Dialog (Stufe 3 kommt aus #43), kein Passwort in Prozessargumenten, kein globaler Authenticator außerhalb des Strategie-Scopes.

### Akzeptanzkriterien

Java 8; Stufen 1+2 vollständig, Stufe 3 injizierbar; Passwort-in-Argv durch Test ausgeschlossen; Kaskaden-Semantik (weiterfallen vs. abbrechen) testfixiert; Inventar aktualisiert.

---

# Kapitel 13: Adyton: Interactive & persistente Secret-Sources (#43)


**Module:** `proasteion:platform:security-prompt`, später `security-store`, `security-dpapi` · **Java 8** · **Prompt zuerst (entsperrt #39-Stufe 3); Store/DPAPI danach**

> ⚠️ **Stand Session 0 (2026-10-06):** Der bestehende KeePassRPC-Adapter ist strukturell ein `SecretMaterialProvider`, seine reflektive Lookup-Bindung passt aber nicht zur gebündelten `keepassrpc-java`-API (`getUserName`/`getPassword` statt `findByRef`/`find`/…); er funktioniert nur gegen Test-Stubs (Inventar §2.1). Damit ist der Prompt-Provider dieses Kapitels auch für den ersten authentifizierten #10-Slice relevant, oder die KeePassRPC-Bindung wird vorher in einem kleinen eigenen Slice mit Library-Integrationstest korrigiert. Neue Secret-Adapter müssen ausdrücklich in die ArchUnit-Whitelists (`RAW_SECRET_MATERIAL_…`, `SECRET_MATERIAL_PROVIDERS_…`) aufgenommen werden.

### Designentscheidungen

1. **Der Prompt ist ein Port, kein Dialog.** `SecretPromptPort` ist ein synchrones Callback-Interface ohne UI-Typen; `exedra` liefert später die Swing-Implementierung, Tests liefern eine programmatische. Damit ist der Provider headless-testbar (Issue-Vorgabe) und die alte MainframeMate-Kopplung (Swing im `KeePassProvider`) strukturell ausgeschlossen — ArchUnit-Core-UI-Regel greift, weil der Provider im `platform`-Modul liegt, das UI-frei bleiben muss (Regel im PR um `platform` erweitern, falls sie das Paket noch nicht abdeckt).
2. **Cancel ist ein Ergebnis erster Klasse:** Der Port liefert `PromptResult` (SECRET | CANCELLED | UNAVAILABLE), der Provider übersetzt CANCELLED in die checked `AuthCancelledException` — genau die Semantik, die PR #14 etabliert hat. Negative-Caching (kurzes Merken einer Cancellation, damit der Dialog nicht sofort wieder aufpoppt) gehört **nicht** in den Provider, sondern in die bestehende `SecretMaterialCache`/`SecretCachePolicy` — Prüfung im PR, ob die Policy Negative-Caching bereits kann (Auth-Analyse §5 fordert es); falls nein, dort ergänzen, nicht hier duplizieren.
3. **Provider-SPI wiederverwenden:** Es existieren `CredentialProvider` und `SecretMaterialProvider` (KeePassRPC implementiert Letzteren). Der Prompt-Provider implementiert **`SecretMaterialProvider`** — dieselbe Schnittstelle wie KeePassRPC, damit der Broker Quellen austauschbar kettet: KeePassRPC → Prompt als geordnete Provider-Liste im Kompositionspunkt. Keine neue Chain-Abstraktion in adyton (die #39-Kette ist Strategie-, nicht Quellen-Ebene — bewusst getrennt halten).
4. **Persistenter Store (Phase 2) = Datei mit AES-GCM,** Master-Key-Bezug hinter `MasterKeyProvider`-Port: Implementierung 1 dateibasiert (portabel, adaptiert `AesCryptoProvider`-Konzept), Implementierung 2 DPAPI via JNA (`security-dpapi`, Windows-only, Phase 3). Format: ein JSON-Objekt `{entryKey: base64(iv+ciphertext)}` — bewusst kein KeePass-kompatibles Format (Nutzer mit KeePass nutzen den RPC-Provider).
5. **KeePass-PS-Variante (KeePass.exe via PowerShell) wird NICHT umgesetzt** — sie war in MainframeMate der langsame, prozesslistige Notnagel; mit RPC (Adapter vorhanden, Anbindung an den realen `keepassrpc-java`-Client offen), Prompt und Store sind drei bessere Wege vorgesehen. Als bewusste Nicht-Migration im Inventar vermerken (siehe Kapitel 18).

### Klassen (Phase 1: Prompt)

#### `SecretPromptPort`

```java
package com.aresstack.corenth.proasteion.platform.security.prompt;

public interface SecretPromptPort {

    final class PromptRequest {
        private final String targetSystem; private final String principal; private final String purpose;
        public PromptRequest(String targetSystem, String principal, String purpose) {
            this.targetSystem = targetSystem; this.principal = principal; this.purpose = purpose; }
        public String targetSystem() { return targetSystem; }
        public String principal() { return principal; }
        public String purpose() { return purpose; }
    }

    final class PromptResult {
        public enum Kind { SECRET, CANCELLED, UNAVAILABLE }
        private final Kind kind; private final char[] secret;
        private PromptResult(Kind kind, char[] secret) { this.kind = kind; this.secret = secret; }
        public static PromptResult secret(char[] s) { return new PromptResult(Kind.SECRET, s); }
        public static PromptResult cancelled() { return new PromptResult(Kind.CANCELLED, null); }
        public static PromptResult unavailable() { return new PromptResult(Kind.UNAVAILABLE, null); }
        public Kind kind() { return kind; }
        /** Transfers ownership; caller must wipe. Single use. */
        public char[] takeSecret() { char[] s = secret; return s; }
    }

    /** Blocking prompt; implementations decide how (dialog, console, test double). */
    PromptResult prompt(PromptRequest request);
}
```

#### `InteractiveSecretMaterialProvider`

Erklärt: adaptiert Port → adyton-SPI. **Verifiziert:** Die SPI-Signatur ist `SecretMaterial resolve(AccessRequest) throws SecretUnavailableException` — sie kennt **kein** `AuthCancelledException`. Das ist ein echter Befund: Cancel ist bei einer interaktiven Quelle semantisch etwas anderes als „nicht verfügbar" (Cancel darf *nicht* zur nächsten Quelle weiterfallen, Unavailable schon). **Vorschlag (Kern dieses PRs):** `resolve` additiv um `throws AuthCancelledException` erweitern — quellcode-kompatibel für bestehende Implementierungen (KeePassRPC wirft sie einfach nie), und der `ProviderBackedAccessBroker` deklariert die Exception in `withAccess`/`acquire` bereits. Bis zur Entscheidung dokumentiert der PR beide Optionen; die Alternative (Cancel als `SecretUnavailableException`-Subtyp) verwischt die Weiterfall-Semantik und wird abgelehnt.

```java
package com.aresstack.corenth.proasteion.platform.security.prompt;

import com.aresstack.corenth.adyton.*;
import java.util.Arrays;

public final class InteractiveSecretMaterialProvider implements SecretMaterialProvider {
    private final SecretPromptPort promptPort;

    public InteractiveSecretMaterialProvider(SecretPromptPort promptPort) {
        if (promptPort == null) throw new IllegalArgumentException("promptPort required");
        this.promptPort = promptPort;
    }

    @Override
    public SecretMaterial resolve(AccessRequest request)
            throws SecretUnavailableException, AuthCancelledException { // throws-Erweiterung s. o.
        SecretPromptPort.PromptResult result = promptPort.prompt(
                new SecretPromptPort.PromptRequest(
                        request.targetSystem(), request.principal(), request.purpose()));
        switch (result.kind()) {
            case CANCELLED:
                throw new AuthCancelledException("user cancelled prompt for " + request.targetSystem());
            case UNAVAILABLE:
                throw new SecretUnavailableException("interactive prompt unavailable");
            case SECRET:
            default:
                char[] secret = result.takeSecret();
                // Verifiziert: SecretMaterialFactory erzeugt intern DefaultSecretMaterial
                // aus (SecretRef-Id, principal, secret). Ownership geht an das Material
                // ueber ("owned by the broker after return") — daher hier NICHT nullen.
                return SecretMaterialFactory.create(
                        request.credentialRef(), request.principal(), secret);
        }
    }
}
```

*(Exakten Fabrikmethoden-Namen beim Implementieren ablesen — verifiziert ist die Fabrik selbst und ihr `DefaultSecretMaterial(secretRef.id(), principal, secret)`-Aufbau; der KeePassRPC-Provider ist die Referenz für die Aufrufform.)*

#### Phase 2: `EncryptedFileSecretStore` + `MasterKeyProvider` (Skizze)

```java
public interface MasterKeyProvider {
    /** @return 32-byte AES key; implementations: file-based key, DPAPI-wrapped key. */
    byte[] loadOrCreateKey() throws java.security.GeneralSecurityException;
}
```

Store implementiert ebenfalls `SecretMaterialProvider` (lesen) plus eine schmale Verwaltungs-API (put/remove) für spätere Settings-UI; AES/GCM/NoPadding, IV pro Eintrag, Datei-Rechte 600.

### ⚠️ Hinweis

Provider-Reihenfolge (KeePassRPC vor Prompt vor Store? Store vor Prompt?) ist Nutzerpräferenz → gehört in die Konfiguration des Kompositionspunkts, nicht hart in adyton. Da typisierte Konfiguration noch fehlt (`ConfigSnapshot`-Follow-up, s. Kapitel 18), Phase 1 mit fester, dokumentierter Reihenfolge KeePassRPC → Store → Prompt ausliefern.

### Tests

Provider: SECRET → Material erzeugt + lokale Kopie genullt; CANCELLED → checked `AuthCancelledException`; UNAVAILABLE → `SecretUnavailableException`; Port-Double mit Aufrufzählung (Negative-Caching-Verhalten wird auf Cache-Ebene getestet, nicht hier); Store: Roundtrip, falscher Key → definierte Exception, IV-Einmaligkeit, Dateirechte; DPAPI: Windows-only-Skip sichtbar.

### Out of scope / do-not-copy

Kein Swing in `platform`; kein `LoginManager`-Retry/Block (Cache-Policy-Thema); kein KeePass-PS; kein Passwort-String-API; keine Settings-UI.

### Akzeptanzkriterien

Java 8; Prompt-Provider headless testbar; Cancel-Semantik checked und getestet; #39-Stufe 3 anschließbar; ArchUnit-Secret-Regeln um neue Module ergänzt und grün; Phase-Schnitt (Prompt jetzt, Store/DPAPI als Folge-PRs im selben Issue) im PR dokumentiert.

---

# Kapitel 14: Pinakes: Semantic-Index-Ports (#7)


**Modul:** `astu:acropolis:chalcotheca:pinakes` · **Java 8** · **Nach stabilem #10-Lifecycle; keine AI-Runtime im ersten PR**

### Designentscheidungen

1. **Nur Ports + In-Memory-Adapter.** Embeddings-Berechnung (ONNX/WinML/HTTP) ist ausdrücklich Adapter-Territorium außerhalb des Kerns (Plan PR 7); die #44-Entscheidung zu winml/onnx bestimmt später den ersten echten Adapter. Der In-Memory-Index rechnet Kosinus-Ähnlichkeit über bereits gelieferte Vektoren — genug, um Hybrid-Retrieval-Verträge zu testen.
2. **Pinakes indexiert `LexicalChunk`-Identitäten, keinen eigenen Chunk-Begriff.** Chunking bleibt in anagraphai (eine Chunk-Wahrheit); pinakes speichert `(VirtualResourceRef, chunkIndex) → Vektor`. Damit sind lexikalisches und semantisches Ergebnis über dieselbe Identität fusionierbar.
3. **`EmbeddingClient` ist asynchronsfrei und batchfähig:** `embed(List<String>) → List<EmbeddingVector>` — Batching ist die einzige Performance-Eigenschaft, die der Port kennen muss; Threading entscheidet der Aufrufer.
4. **Hybrid-Fusion als reine Funktion:** `HybridRetrievalPlan` gewichtet lexikalische und semantische Treffer (Reciprocal Rank Fusion als Default) ohne I/O — vollständig unit-testbar, adaptiert `HybridRetriever` aus dem Research-Code ohne dessen `RagService`-Singleton.

### Ports (Kern des PRs)

```java
package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import com.aresstack.corenth.astu.VirtualResourceRef;
import java.util.List;

public interface SemanticIndex extends java.io.Closeable {
    void upsert(VirtualResourceRef ref, int chunkIndex, EmbeddingVector vector, String textExcerpt);
    void removeAll(VirtualResourceRef ref);
    List<SemanticSearchResult> search(EmbeddingVector query, int limit);
}
```

```java
package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import java.util.Arrays;

public final class EmbeddingVector {
    private final float[] values;
    public EmbeddingVector(float[] values) {
        if (values == null || values.length == 0) throw new IllegalArgumentException("empty vector");
        this.values = Arrays.copyOf(values, values.length);
    }
    public int dimension() { return values.length; }
    public float[] copyValues() { return Arrays.copyOf(values, values.length); }
    public double cosineSimilarity(EmbeddingVector other) {
        if (other.values.length != values.length) throw new IllegalArgumentException("dimension mismatch");
        double dot = 0, a = 0, b = 0;
        for (int i = 0; i < values.length; i++) {
            dot += values[i] * other.values[i]; a += values[i] * values[i]; b += other.values[i] * other.values[i];
        }
        return (a == 0 || b == 0) ? 0 : dot / (Math.sqrt(a) * Math.sqrt(b));
    }
}
```

```java
package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import java.util.List;

public interface EmbeddingClient {
    int dimension();
    List<EmbeddingVector> embed(List<String> texts) throws EmbeddingException;
}
```

*(Weitere Typen: `SemanticSearchResult` (ref, chunkIndex, score, excerpt), `Reranker`-Port (`rerank(query, List<SemanticSearchResult>)`), `EmbeddingException`. `InMemorySemanticIndex`: `ConcurrentHashMap` + lineare Kosinus-Suche — für Ports-Tests ausreichend, Skalierung ist Adapter-Sache.)*

### Konflikt-Hinweis

`SemanticSearchResult` und anagraphais `LexicalSearchResult` sollen fusionierbar sein → beide brauchen `(ref, chunkIndex, score)`. `LexicalSearchResult` hat chunkIndex bereits (anagraphai-Inventar). Fusion lebt in pinakes (`HybridRetrievalPlan`), darf aber anagraphai-Typen referenzieren? **Ja** — beide sind chalcotheca-Kinder, ArchUnit `ANAGRAPHAI_MUST_STAY_LEXICAL_ONLY` verbietet nur die Gegenrichtung. Im PR verifizieren.

### Tests / Akzeptanz

Kosinus inkl. Null-/Dimension-Fällen; Upsert überschreibt; removeAll je Ref; RRF-Fusion deterministisch bei Gleichstand; kein ONNX/HTTP/Cloud im Kern (ArchUnit-Ergänzung); Java 8; README dokumentiert „Ports-only, Runtime folgt #44".

---

# Kapitel 15: Propylaea: Source-Code-Modell (#3)


**Modul:** `astu:propylaea` · **Java 8** · **Model-first (Plan PR 8); Parser-Adaption folgt in Folge-PRs**

### Designentscheidungen

1. **Sprachneutrales Modell vor jedem Parser.** Aus dem Research-Code (`JCLLexer.g4`, `NaturalParser`, `CobolParser`, `CallExtractor`, `ExternalCall`) wird zunächst nur das *Ergebnismodell* destilliert: Komponenten, Relationen, Positionen. Die ANTLR-Grammatiken kommen erst im zweiten PR als `propylaea-jcl`-Adapter (eigenes Submodul wegen ANTLR-Runtime — Isolationsmuster wie #42).
2. **Relationen sind typisierte Kanten, keine Subklassen-Hierarchie:** `CodeRelation(kind: CALLS | INCLUDES | READS | WRITES, from, to)` — deckt `CallRelation`/`DataAccessRelation`/`IncludeRelation` aus dem Plan mit einem Typ ab; neue Kinds sind additiv. Weniger Klassen, gleiche Ausdruckskraft.
3. **`to` darf unaufgelöst sein:** Externe Calls referenzieren oft Programme, die (noch) nicht im Bestand sind → `CodeComponentRef` trägt entweder eine aufgelöste `VirtualResourceRef` oder nur einen symbolischen Namen. Auflösung ist ein späterer Acropolis-Schritt, kein Parser-Problem.
4. **Anbindung an deigma über `ContentCategory.SOURCE_CODE`:** deigma routet (Kategorie existiert bereits genau dafür, deigma-Inventar); propylaea konsumiert den extrahierten Text + Sprach-Hint. Kein eigener Datei-Zugriff in propylaea (bleibt astu-rein).

### Kernmodell

```java
package com.aresstack.corenth.astu.propylaea;

public enum SourceLanguage { JCL, NATURAL, COBOL, DDM, UNKNOWN }
```

```java
package com.aresstack.corenth.astu.propylaea;

public final class SourceLocation {
    private final int startLine; private final int endLine;
    public SourceLocation(int startLine, int endLine) {
        if (startLine < 1 || endLine < startLine) throw new IllegalArgumentException("invalid range");
        this.startLine = startLine; this.endLine = endLine;
    }
    public int startLine() { return startLine; }
    public int endLine() { return endLine; }
}
```

```java
package com.aresstack.corenth.astu.propylaea;

public final class CodeComponent {
    public enum Kind { PROGRAM, SUBROUTINE, JOB, STEP, COPYBOOK, DATA_DEFINITION }
    private final String name; private final Kind kind; private final SourceLocation location;
    public CodeComponent(String name, Kind kind, SourceLocation location) {
        if (name == null || name.isEmpty() || kind == null) throw new IllegalArgumentException("name/kind required");
        this.name = name; this.kind = kind; this.location = location;
    }
    public String name() { return name; }
    public Kind kind() { return kind; }
    public SourceLocation location() { return location; }
}
```

```java
package com.aresstack.corenth.astu.propylaea;

public interface SourceParser {
    boolean supports(SourceLanguage language);
    ParsingResult parse(ParsingRequest request); // result = ProgramStructure oder Failure mit Reason
}
```

*(Weitere Typen kompakt: `CodeComponentRef` (symbolisch/aufgelöst), `CodeRelation`, `ProgramStructure` (Komponenten + Relationen + Sprache), `ParsingRequest` (ref, language, text), `ParsingResult` (success/failure, Warnungen), `SourceParserRegistry` nach dem `ExtractionRegistry`-Muster, `LanguageDetector`-Port mit Endungs-/Inhalts-Heuristik als Default.)*

### do-not-copy / Tests / Akzeptanz

Nicht kopieren: `JclOutlineModel`, `JclElementType` (UI-Icons), RSyntaxTextArea-TokenMaker, Mermaid-UI-Konvertierung. Tests: Modell-Invarianten, Registry-Dispatch, Detector-Heuristik, ein handgeschriebener Mini-„Parser" (Zeilen-Regex für NATURAL `CALLNAT`) als Vertrags-Demo — ausdrücklich als Platzhalter markiert. Akzeptanz: Java 8; astu-rein (keine deigma-/proasteion-Imports — Sprach-Hint kommt als Parameter); README mit Parser-Roadmap (JCL → Natural → COBOL) und Verweis auf Research-Grammatiken.

---

# Kapitel 16: Katagogion: Plugin-/Tool-/MCP-Ports (#12)


**Modul:** `proasteion:katagogion` · **Java 8** · **Ports-first; blockiert durch #44 (wd4j-Disposition) nur für den MCP-Adapter, nicht für die Ports**

### Designentscheidungen

1. **Tools sind Capability-Empfänger, keine Systemzugreifer.** Der zentrale Sicherheitsschnitt: `ToolPort.execute(ToolInvocation, ToolContext)` — und `ToolContext` enthält *ausschließlich* mediierte Fähigkeiten (Suche über den Acropolis-`SearchCoordinator`-Pfad, Ressourcen-Lesen über `MediatedResourceService`-Sicht). Kein Filesystem, kein Netzwerk, kein holkas, kein adyton im Kontext. Die bestehenden ArchUnit-Regeln (`katagogion ↛ holkas/tamias`) erzwingen das bereits — der PR ergänzt die Regel um `katagogion ↛ java.io.File`-Prüfung auf Tool-Implementierungsebene? Nein — zu radikal für ein Framework-Modul; stattdessen: Tools aus *Plugins* erhalten nur den Kontext, und der Kontext-Typ ist das Audit-Objekt. Im PR als Sicherheitsmodell-Abschnitt dokumentieren.
2. **MainframeMate-Konzepte adaptieren, Runtime nicht:** `ToolSpec` (Name, Beschreibung, Parameter-Schema als einfache Map — kein JSON-Schema-Lib-Zwang), `ToolRegistry`, `PluginDescriptor` + ServiceLoader-Idee. Nicht: `MainframeContext` (JFrame/Tabs), Plugin-Singletons, Menu-Command-Kopplung.
3. **MCP ist ein Adapter über den Ports, kein Port selbst.** Ob der MCP-Server aus `wd4j-mcp-server` adaptiert wird oder ein schlanker neuer entsteht, entscheidet #44. Die Ports hier sind so geschnitten, dass ein MCP-Server sie 1:1 exponieren kann (`ToolSpec` ≈ MCP-Tool-Deklaration, `ToolInvocation` ≈ tools/call).
4. **Sandbox-Policy als Entscheidungs-Port (`PluginSandboxPolicy`),** nicht als Implementierung — echtes Sandboxing (Classloader-Isolation) ist ein Folge-Issue; Phase 1 liefert nur die Deklarations- und Entscheidungsebene (welches Plugin darf welche Capability).

### Kern-Ports

```java
package com.aresstack.corenth.proasteion.katagogion;

import java.util.Collections;
import java.util.Map;

public final class ToolSpec {
    private final String name; private final String description;
    private final Map<String, String> parameterDescriptions; // name -> human description
    public ToolSpec(String name, String description, Map<String, String> parameterDescriptions) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("name required");
        this.name = name; this.description = description == null ? "" : description;
        this.parameterDescriptions = parameterDescriptions == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new java.util.LinkedHashMap<String, String>(parameterDescriptions));
    }
    public String name() { return name; }
    public String description() { return description; }
    public Map<String, String> parameterDescriptions() { return parameterDescriptions; }
}
```

```java
package com.aresstack.corenth.proasteion.katagogion;

import java.util.Map;

public interface ToolPort {
    ToolSpec spec();
    ToolResult execute(Map<String, String> arguments, ToolContext context) throws ToolExecutionException;
}
```

```java
package com.aresstack.corenth.proasteion.katagogion;

/** The ONLY capabilities a tool receives. Everything is mediated; nothing raw. */
public interface ToolContext {
    MediatedSearch search();     // thin facade over acropolis search path
    MediatedReading reading();   // thin facade over mediated resource access (read-only)
}
```

*(Dazu: `ToolResult` (text + optionale strukturierte Map), `ToolRegistry` (register/lookup/list, Duplikat-Reject wie holkas-Registry), `CorenthPlugin` (`descriptor()`, `registerTools(ToolRegistry)`), `PluginDescriptor` (id, version, requestedCapabilities), `PluginSandboxPolicy.evaluate(descriptor) → Allow/Deny+Reason`, ServiceLoader-Discovery `PluginLoader`. `MediatedSearch`/`MediatedReading` sind schmale Interfaces im katagogion-Modul, implementiert im `application`-Kompositionspunkt — Richtung proasteion → astu bleibt gewahrt.)*

### Konflikt-Hinweis

MainframeMates Tool-Tests (`GrepSearchToolTest`, `ReadFileToolTest`, `StatPathToolTest`) beweisen Tools mit *direktem* Dateizugriff — genau das verbietet das neue Modell. Die migrierten Pendants (`SearchTool`, `ReadResourceTool`) laufen über `ToolContext` und werden gegen Fakes getestet; die alten Tests dienen nur als Verhaltens-Spezifikation der Ausgaben. Im PR explizit machen, sonst „korrigiert" ein Agent die Fakes zu echtem IO.

### Tests / Akzeptanz

Registry-Duplikat-Reject; ServiceLoader lädt Test-Plugin; SandboxPolicy Deny verhindert Registrierung; zwei Referenz-Tools (Search, ReadResource) gegen Context-Fakes; ToolResult-Serialisierbarkeit (einfach genug für MCP); ArchUnit bestehende Regeln + keine adyton-Imports; Java 8; README mit Sicherheitsmodell und #44-Abhängigkeit für MCP-Adapter.

---

# Kapitel 17: Migration: Research-Disposition (#44)


**Kein Code — Entscheidungsdokument.** Ergebnis wird `docs/migration/research-disposition.md` + Inventar-Update. **Entsperrt den MCP-Zuschnitt von #12.**

### Designentscheidungen (Vorschlag zur Beschlussfassung)

Bewertungsraster je Modul: *Kernnähe* (dient Suche/Index/Ressourcen?), *Kopplung* (Swing/Singleton-Grad), *Ersetzbarkeit* (gibt es das als Produkt/Bibliothek?), *Pflegelast*. Daraus eine von drei Dispositionen: **MIGRATE** (bekommt Issue), **EXTERNAL** (eigenes Repo/Abhängigkeit), **DO_NOT_MIGRATE** (bleibt Research-Referenz).

### Entscheidungsmatrix (Vorschlag)

| Modul (research/) | Umfang | Kernnähe | Vorschlag | Begründung |
|---|---|---|---|---|
| `wd4j` + `wd4j-mcp-server` | 255 Dateien | niedrig | **EXTERNAL** | Eigenständiges WebDriver-BiDi-Produkt; Corenth braucht höchstens den MCP-*Server-Rahmen* als Konzeptreferenz für #12. Eigenes Repo `aresstack/wd4j`, Corenth referenziert nichts davon. |
| `mermaid`-Rendering | klein | niedrig | **DO_NOT_MIGRATE** (vorerst) | Diagramm-Preview ist Exedra-Komfort; erst nach UI-Ausbau als Exedra-Plugin-Kandidat neu bewerten. |
| `betaview` | mittel | niedrig | **DO_NOT_MIGRATE** | Legacy-Viewer; Nutzen durch deigma-Extraktion + Exedra-Preview abgedeckt. |
| `dosbox`-Integration | klein | keine | **DO_NOT_MIGRATE** | Emulator-Steuerung ist orthogonal zum Wissens-Backend. |
| `winml` / `onnx` | mittel | mittel | **MIGRATE (als Adapter, später)** | Einziger lokaler Embedding-Pfad → wird der erste `EmbeddingClient`-Adapter (#7), aber erst nach Ports-PR; bis dahin ruhen lassen. Follow-up-Issue erst bei #7-Abschluss anlegen. |
| `video`/Medien | klein | keine | **DO_NOT_MIGRATE** | Außerhalb des Produktkerns. |
| Excel-Import-Plugin | klein | mittel | **MIGRATE (deigma-Fall)** | Bereits im deigma-Inventar als Import-Sonderfall notiert; nach #42 als `deigma-office`-Erweiterung oder katagogion-Tool neu schneiden. |

### Konsequenzen

1. Issue #12 verliert die wd4j-Unbekannte: MCP-Adapter wird ein schlanker neuer Server über den katagogion-Ports (wd4j-mcp-server nur als Lesereferenz).
2. `research/`-Verzeichnis kann nach Abschluss aller MIGRATE-Posten um EXTERNAL/DO_NOT_MIGRATE-Anteile erleichtert werden (eigenes Aufräum-Issue erst dann — vorher ist es wertvolle Referenz).
3. Jede Zeile der Matrix wird im Inventar §-Struktur nachgeführt; DO_NOT_MIGRATE erhält eine Ein-Satz-Begründung im Disposition-Dokument (Nachvollziehbarkeit für spätere Ich-Instanzen und Agenten).

### Akzeptanzkriterien

Disposition-Dokument committed; Inventar aktualisiert; #12-Issue-Text um wd4j-Entscheidung ergänzt; keine Code-Änderung.

---

# Kapitel 18: Vergessene Migrationen aus MainframeMate


**Prüfmethode:** Alle Top-Level-Pakete von `research/app` (nach Dateizahl) gegen Migrations-Inventar, offene Issues (#3–#45) und die Kapitel 1–17 dieses Kompendiums abgeglichen. Ergebnis: **5 echte Lücken**, 2 bewusste Nicht-Migrationen (bestätigen), 1 Rest-Sichtung.

### 1. LLM-Provider-Anbindung und Chat-/RAG-Orchestrierung — **größte Lücke, kein Issue, kein Zielmodul**

Fundstellen: `service/OllamaChatManager`, `LlamaCppChatManager`, `CloudChatManager`, `CustomChatManager`, `chat/*` (9 Dateien, u. a. `attachment/BuildHiddenContextUseCase`, `AttachmentContextBuilder`), `rag/service/RagService`, `rag/usecase/HybridRetriever` + `RagContextBuilder`.

Das ist die halbe Daseinsberechtigung des Produkts: die Verbindung von Suche/Index zu einem Sprachmodell (lokal via Ollama/llama.cpp, Cloud, custom) samt Kontextaufbau (versteckter Kontext aus Attachments + Retrieval-Treffern). Der Plan hat Retrieval-*Verträge* nach pinakes (#7) gelegt — aber **Provider-Clients, Prompt-/Kontext-Assemblierung und Chat-Sitzungsführung haben keinen Ort**: nicht in pinakes (das ist Index, nicht Dialog), nicht in katagogion (Tools ≠ Chat), nicht in exedra (UI-Shell soll dünn bleiben).

**Vorschlag:** Neues Issue „symposion (o. ä.) — LLM-Provider-Ports und Kontext-Assemblierung": ein astu-nahes Modul mit Ports `ChatModelClient` (streamfähig, providerneutral), `ContextAssembler` (Retrieval-Treffer + Attachments → Prompt-Kontext, adaptiert `RagContextBuilder`/`BuildHiddenContextUseCase`), `ConversationLog`; Provider-Implementierungen (Ollama-HTTP zuerst — kleinster Client) als proasteion-Adapter. `HybridRetriever`-Fusionslogik geht wie geplant in #7; die *Nutzung* der Fusion gehört hierher. Ohne dieses Issue endet Corenth als Suchmaschine ohne das „Antworten"-Feature.

### 2. Workflow-Engine — kein Issue, keine Disposition

Fundstellen: `workflow/engine/*` (Lexer, ExpressionParser, CompositeExpression, LiteralExpression, Token, Blocking-/PollingResolutionContext), `workflow/WorkflowRunnerImpl`, `helper/WorkflowStorage`, dazu `runtime/ExpressionCompiler*` (JShell-/Java8-Compiler!), `runtime/VariableRegistryImpl`, `runtime/SentenceTypeRegistryImpl`, `ui/components/WorkflowPanel` (11 + 5 Dateien Kern).

Eine eigene kleine Ausdruckssprache mit Lexer/Parser plus zwei Kompilierstrategien (JShell, Java-8-Inline) — nirgends im Inventar, in keinem Issue, in keiner #44-Zeile. Das ist funktional verwandt mit dem #10-Run-Modell (beide orchestrieren Schritte), aber deutlich mächtiger (nutzergetriebene Abläufe mit Variablen und Ausdrücken).

**Vorschlag:** In #44 als eigene Matrix-Zeile aufnehmen und dort entscheiden. Meine Empfehlung: **DO_NOT_MIGRATE für die Engine** (JShell-Abhängigkeit ist Java-8-feindlich — JShell existiert erst ab 9! —, die Ausdruckssprache ist Wartungslast) und stattdessen prüfen, ob künftige Automatisierung über katagogion-Tools + #10-Runs abgedeckt wird. Falls Angelo die Workflows aktiv nutzt: eigenes Issue mit reduziertem Scope (Runner-Konzept ohne Expression-Compiler). Die Entscheidung muss aber *bewusst* fallen — aktuell fällt das Modul einfach durchs Raster.

### 3. Typisierte Konfiguration — versprochen, nie als Issue angelegt

Fundstellen: `model/Settings` (mit `AiProvider`, `FileEndingOption`, `MouseFkeyBinding` …), `helper/SettingsHelper` + vier weitere Settings-Helper; Corenth-Seite: `astu/ConfigSnapshot.java` existiert als Placeholder, und das astu-Inventar verweist auf ein „Follow-up-Issue" für typisierte Konfiguration — **dieses Issue wurde nie erstellt**.

Die Lücke wird gerade akut: #43 braucht Provider-Reihenfolge, #5 braucht Policy-Parameter, #37/#38 brauchen Site-Registries, die Kompositionswurzel (#10-ADR) braucht einen Ladeort. Jedes dieser TODO-Dokumente behilft sich mit „fest verdrahtet im Kompositionspunkt".

**Vorschlag:** Issue „astu/application — typed configuration loading" anlegen: `ConfigSnapshot` ausdefinieren (unveränderlich, sektioniert je Modul), Ladeadapter (JSON-Datei im Userverzeichnis) in `proasteion:application`, **keine** Secrets in der Config (adyton-Grenze), Migrationspfad von MainframeMates `settings.json` explizit out of scope. Priorität: nach #10-Slice-1, vor #43-Phase-2.

### 4. Dependency-Graph-Dienste — von #3 nur halb abgedeckt

Fundstellen: `service/NaturalDependencyGraph`, `service/JclDependencyService`, `jcl/*` (8 Dateien).

#3 migriert Parsing und Call-*Extraktion* (Relationen pro Datei). Die Aggregation zum **ressourcenübergreifenden Abhängigkeitsgraphen** (wer ruft wen, transitiv, über den ganzen Bestand) ist ein Acropolis-Anliegen (arbeitet über Bronze-Bestand + propylaea-Ergebnisse) und in keinem Issue benannt.

**Vorschlag:** Kein neues Issue jetzt (hängt vollständig an #3), aber im #3-Issue-Text einen Follow-up-Marker ergänzen: „Graph-Aggregation über Ressourcen hinweg = separates acropolis-Issue nach Modell-PR". Sonst geht es beim Abschluss von #3 verloren.

### 5. „Überall-Suche"-Backlog — ungesichtetes Konzeptdokument

Fundstelle: `research/docs/ueberall-suche-issue-backlog.md` — fertig formulierte Arbeitspakete zur Such-Föderation (Quellen-Inventar, Verfügbarkeits-Transparenz, Refresh-Strategien je Quelle). Das betrifft direkt den bestehenden `SearchCoordinator` (acropolis) und die #10/#5-Welt, ist aber in keine Issue-Formulierung eingeflossen.

**Vorschlag:** Beim nächsten GPT-Durchgang das Dokument gegen #5/#10/#33 abgleichen; was nicht abgedeckt ist (v. a. „Quelle meldet Verfügbarkeit/Leerlauf-Gründe" — passt zu den ReasonCode-Mustern), als Kommentar in die bestehenden Issues übernehmen statt neue anzulegen.

### Bewusste Nicht-Migrationen (nur bestätigen, nichts tun)

- **KeePass-PS-Provider** (KeePass.exe via PowerShell): in Kapitel 13 explizit als nicht-migriert entschieden — mit RPC (Adapter vorhanden, reale Client-Anbindung noch offen, s. Kapitel 13), Prompt und Store sind drei bessere Quellen vorgesehen. Im Inventar als „bewusst verworfen" führen, damit die Frage nicht wiederkehrt.
- **UI-Schwergewichte** (`ui/` 237 Dateien, `betaview`, `video`, `dosbox`, Mermaid-Preview): via #44-Matrix bzw. Exedra-Ausbau-Zukunft abgedeckt; keine stille Lücke, sondern vertagte Entscheidungen mit Ort.

### Rest-Sichtung (niedrig, der Vollständigkeit halber)

`helper/BookmarkHelper` + `ShortcutManager` (→ exedra-Ausbau, UI-Territorium), `event/` (EventBus-Konzept bereits in exedra migriert), `util/` (22 Dateien — beim jeweiligen Feature-Issue mitprüfen, nie pauschal kopieren), `net/` (Routing bereits in `platform:network`), `runtime/PluginManager` + `ToolRegistryImpl` (durch #12-Neuschnitt ersetzt), `files/` (35 Dateien — größtenteils durch holkas/deigma ersetzt; beim #44-Durchgang stichprobenartig gegenprüfen).

### Zusammenfassung der Handlungsempfehlungen

1. **Neues Issue: LLM-Provider + Kontext-Assemblierung** (Lücke 1) — einziges fachlich kritisches Loch.
2. **#44-Matrix um Workflow-Engine-Zeile erweitern** (Lücke 2) und bewusst entscheiden.
3. **Neues Issue: typisierte Konfiguration** (Lücke 3) — technisch bald blockierend.
4. **Follow-up-Marker in #3** für Graph-Aggregation (Lücke 4).
5. **Backlog-Dokument sichten** und in bestehende Issues einarbeiten (Lücke 5).

---
