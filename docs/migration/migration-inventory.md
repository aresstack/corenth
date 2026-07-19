# MainframeMate → Corenth — Master-Migrationsinventar

**Stand:** Codeprüfung 2026-07-19 gegen `main` @ `122f999` ("Add trusted MVS session auth adapter", 2026-06-17); Tracker-, Headless- und Lückenbereinigung aktualisiert am 2026-07-19
**Zweck:** Eine einzige, laufend pflegbare Landkarte: Welcher MainframeMate-Referenzbestand (`research/`, ~1.400 Java-Dateien) ist in welcher Form in der Corenth-Zielarchitektur angekommen, was ist bewusst ausgeschlossen, was steht aus. Ergänzt die modulspezifischen Inventare, ersetzt sie nicht.

Leitprinzip aus [mainframemate-migration.md](mainframemate-migration.md):

```text
MainframeMate-Code = Beleg und Erfahrungsquelle
Corenth-Code      = saubere Reimplementierung entlang der neuen Modulgrenzen
```

## Status-Legende

| Status | Bedeutung |
| --- | --- |
| ✅ migriert | Konzept in Corenth reimplementiert, mit Tests |
| 🟡 teilweise | Kern vorhanden, dokumentierte Teile offen |
| 🔧 in Arbeit | aktive Entwicklung erkennbar (jüngste Commits) |
| ⬜ offen | Zielmodul existiert, Migration nicht begonnen |
| 🚫 do-not-copy | bewusst nicht migriert (Kopplung/UI/Architekturverstoß) |
| ❓ ungeklärt | kein Corenth-Ziel definiert — Entscheidung ausstehend |

---

## 1. Zielarchitektur-Konformität (Ist-Prüfung)

Die Boundary-Regeln aus [architecture-notes.md](../architecture-notes.md) sind nicht nur dokumentiert, sondern größtenteils per ArchUnit erzwungen (`architecture-tests/CorenthArchitectureRulesTest`):

| Regel (Doc) | Durchsetzung | Status |
| --- | --- | --- |
| `astu`/`adyton` ↛ `proasteion` | ArchUnit `INNER_CITY_MUST_NOT_DEPEND_ON_OUTER_RING` | ✅ erzwungen |
| Core ↛ Swing/AWT/JavaFX | ArchUnit `CORE_MUST_NOT_DEPEND_ON_UI_TECHNOLOGY` | ✅ erzwungen |
| `exedra`/`katagogion` ↛ `holkas` (Mediated-Access-Pflicht) | ArchUnit `CLIENT_ADAPTERS_MUST_NOT_BYPASS_MEDIATED_RESOURCE_ACCESS` | ✅ erzwungen |
| `exedra`/`katagogion` ↛ `tamias` | ArchUnit `CLIENT_ADAPTERS_MUST_NOT_BYPASS_RESOURCE_POLICY` | ✅ erzwungen |
| Rollenreinheit holkas/deigma/tamias/anagraphai/exedra | je eigene ArchUnit-Regel | ✅ erzwungen |
| Raw `SecretMaterial` nur in `adyton` + vertrauenswürdigen Secret-Adaptern | ArchUnit Secret-Containment-Regeln | ✅ erzwungen |
| **Mediated bronze access als Primärpfad** | — | ⚠️ **nur in Tests verdrahtet, kein produktiver Kompositionspunkt** |

**Zentrale Lücke:** Es fehlt keine Klasse der Kette, sondern die produktive Komposition. `MediatedResourceService` (Chalcotheca-Schalter) und `ResourceLifecycleCoordinator` (Indexing-Skeleton) existieren beide, werden aber ausschließlich von Tests instanziiert (`MediatedHolkasFileSliceTest`, `WalkingSkeletonIntegrationTest`). Zudem fehlt im vermittelten Datenfluss eine Adyton-gestützte Access-Preparation-Station für authentifizierungspflichtige externe Beschaffung. Issue #10 verlangt nun außerdem vor dem Bootstrap-Code eine explizite Entscheidung über dessen neutralen Modulort; `proasteion`-Root und eine ausschließlich Exedra/Swing-gebundene Komposition sind ausgeschlossen. → Nächste Schritte in §5.

---

## 2. Migrationstabelle: research/ → Corenth

### 2.1 Kern & Sicherheit

| research/-Quelle | Umfang | Corenth-Ziel | Status | Issue | Detailinventar |
| --- | --- | --- | --- | --- | --- |
| `core/files/auth`, `app/util` (CredentialStore, SessionCipher, Crypto-Provider) | ~16 | `adyton` | ✅ migriert (Boundary, Broker, Lease, Cache, Strategien) | #1/#14 ✔ | [adyton-inventory](mainframemate-adyton-inventory.md) |
| `app/util/KeePassRpcClient`, `KeePassProvider` (RPC-Teil) | ~3 | `proasteion:platform:security-keepassrpc` | ✅ migriert (`KeePassRpcSecretMaterialProvider`) | — | — *(Inventar fehlt)* |
| Interactive-Prompt-, DPAPI-/PowerShell-/AES-Secret-Source-Adapter | ~4+ | vertrauenswürdige `proasteion:platform:security-*`-Adapter hinter Adyton-Ports | ⬜ offen — Prompt zuerst, persistenter Store/DPAPI danach | #43 | [Auth-Analyse §8](../analysis/mainframemate-authentication-flows.md) |
| `KeePassRpcPairingDialog`, `LoginManager`-Swing-Teile | ~3 | — | 🚫 do-not-copy (UI im Vault verboten) | — | adyton-inventory |
| `win-proxy`, Proxy-PS-Skripte | ~10 | `proasteion:platform:network(-winproxy)` | ✅ migriert | — | — *(Inventar fehlt)* |

### 2.2 Ressourcenmodell, Archiv, Policy, Orchestrierung

| research/-Quelle | Umfang | Corenth-Ziel | Status | Issue | Detailinventar |
| --- | --- | --- | --- | --- | --- |
| `core/files/path` (VirtualResourceRef, PathDialect), `BookmarkEntry`, `ScannedItem` | ~8 | `astu` (BookmarkUri, ResourceScheme, Metadata, Fingerprint) | ✅ migriert | #2/#17 ✔ | [astu-inventory](mainframemate-astu-inventory.md) |
| `app/ui/VirtualResource*` (UI+Backend-State vermischt) | ~4 | — | 🚫 do-not-copy | — | astu-inventory |
| `archive` (CacheRepository, ArchiveRun, Hashing, Snapshots) | 6 | `chalcotheca` (Bronze-Modell, ResourceArchive, MediatedResourceService) | 🟡 teilweise — In-Memory ja; **Resource Records, Versionierung und Lifecycle-State offen** | #33 offen | — *(Inventar fehlt)* |
| `indexing/model/IndexSource` (scope, patterns, depth, size, changeDetection …) | ~7 | `tamias` | 🟡 teilweise — AccessPolicy + `IndexingRule`/Pattern ja; **ChangeDetectionStrategy, CacheInvalidationPolicy, ResourceScope/Depth/Size fehlen** | #5 neu zugeschnitten | — *(Inventar fehlt)* |
| `indexing/service/IndexingPipeline`, `IndexRunStatus` | ~7 | `acropolis` (produktive Mediated-Komposition + Run/Plan/Step/Status) | ⬜ offen — Coordinator ist Walking Skeleton; produktive Komposition und Run-Modell fehlen | #10 neu formuliert; Bootstrap-Ort explizit zu entscheiden | [Plan PR 5](corenth-mainframemate-backend-reimplementation-plan-2026-06-02.md) |
| `indexing/connector/SourceScanner` (scan → fetch → process) | ~4 | `emporion` (ResourceHarbor, HarborRequest/Result/Inspection) | ✅ migriert (vereinfachte Harbor-Pipeline) | #15 geschlossen | — *(Inventar fehlt)* |

### 2.3 Connectors (holkas) & Extraktion (deigma)

| research/-Quelle | Umfang | Corenth-Ziel | Status | Issue | Detailinventar |
| --- | --- | --- | --- | --- | --- |
| `core/files/api` (FileService, FileNode, FilePayload) | ~6 | `holkas` SPI (ResourceConnector[Registry], Listing, ReadMode, RawResource*) | ✅ migriert | #8 geschlossen | [Plan PR 2](corenth-mainframemate-backend-reimplementation-plan-2026-06-02.md) |
| `files/impl/local` | ~4 | `holkas` `FileSystemResourceConnector` | ✅ migriert | #8 geschlossen | — |
| `files/impl/ftp` + MVS (CommonsNet, MvsPathDialect, Listing, QuoteNormalizer) | ~14 | `holkas/ftp` + `holkas/mvs` + adyton-Strategie (`MvsFtpAuthenticationStrategy`) | 🔧 in Arbeit — Session/AccessHandle/Dialekt vorhanden | #8 geschlossen; Folgeslices separat | — *(Inventar fehlt — anlegen, dient als Vorlage für NDV)* |
| `files/impl/ftp/jes` (JES Submit/Spool) | ~3 | `holkas` (JES über vorhandenen `FtpAccessHandle`) | ⬜ offen | #35 | Auth-Analyse §2/§8 |
| `ndv/**`, `files/impl/ndv` | **172** | `holkas`-Adapter + adyton `NdvAuthenticationStrategy`/`NdvAccessHandle` | ⬜ offen — größter unmigrierter Connector | #34 | Auth-Analyse §2/§8 |
| `mail` (lokale PST/OST) | 6 | `holkas` mail + `deigma` Attachments | ⬜ offen — bewusst ohne spekulativen Auth-Pfad | #36 | deigma-inventory |
| `wiki-integration`, `wiki` | Teil von ~28 | `holkas` MediaWiki + Token-/Cookie-Session-Handle | ⬜ offen | #37 | Auth-Analyse §2/§4/§8 |
| `confluence` | Teil von ~28 | `holkas` Confluence + Basic-/mTLS-Strategien | ⬜ offen | #38 | Auth-Analyse §2/§4/§8 |
| `sharepoint` | Teil von ~28 | `holkas` SharePoint + SSO-Kaskade/kontrollierter Fallback | ⬜ offen | #39 | Auth-Analyse §2/§4/§8 |
| `ingestion` (Detector, Registry, Document/Block, PlainText/Markdown) | 11 | `deigma` | ✅ migriert (Kern) | #9/#22 ✔ | [deigma-inventory](mainframemate-deigma-inventory.md) |
| `ingestion`-Schwer-Extraktoren (PDF/DOCX/XLSX/HTML/Tika), `RecordStructureCodec` | ~7 | isolierte `deigma`-Implementierungsadapter | ⬜ offen — für reale Dokumente und #36-Attachments erforderlich | #42 | deigma-inventory |

### 2.4 Indizes & Analyse

| research/-Quelle | Umfang | Corenth-Ziel | Status | Issue | Detailinventar |
| --- | --- | --- | --- | --- | --- |
| `rag` lexikalisch (LuceneLexicalIndex, Chunk, PR-#51-Chunking) | ~10 | `anagraphai` (+`chunking`) | ✅ migriert | #6/#21, #24/#25 ✔ | [anagraphai-inventory](mainframemate-anagraphai-inventory.md) |
| `rag` semantisch (SemanticIndex, EmbeddingClient, HybridRetriever, Reranker) | ~10 | `pinakes` (ports-first) | ⬜ offen (0 Klassen) | #7 | [Plan PR 7](corenth-mainframemate-backend-reimplementation-plan-2026-06-02.md) |
| `jcl` (ANTLR-Grammatiken), `service/codeanalytics` (Natural/COBOL/DDM-Parser, CallExtractor) | ~15 | `propylaea` (Model-first, Parser als Adapter) | ⬜ offen (0 Klassen) | #3 | [Plan PR 8](corenth-mainframemate-backend-reimplementation-plan-2026-06-02.md) |
| `mcp`, `runtime`, `plugins` (ToolSpec, ToolRegistry, PluginManager) | ~35 | `katagogion` (ports-first, Tools nur über Mediated Ports) | ⬜ offen (0 Klassen) | #12 | [Plan PR 9](corenth-mainframemate-backend-reimplementation-plan-2026-06-02.md) |

### 2.5 UI

| research/-Quelle | Umfang | Corenth-Ziel | Status | Issue | Detailinventar |
| --- | --- | --- | --- | --- | --- |
| `ui`-Shell (MainFrame, Drawer, ToolTabRegistry, Settings-Shell), `toolbar-kit`, `event` | ~254 | `exedra` (generisches Shell-Framework) | ✅ migriert — **eingefroren**, Business-Panels bewusst nicht | #28/#29 ✔, #30 geschlossen | exedra/README |
| Thin-Adapter-Grenze für Business-UI | — | `exedra` bleibt austauschbarer Adapter; `EXEDRA_MUST_STAY_THIN_UI_SHELL` | ✅ dokumentiert und erzwungen | #11 geschlossen | Plan „Korrektur zu #30/#11“ |
| Exedra Headless-Tests | 50 Testfälle | leichtgewichtige Swing-Tests laufen headless; zwei displaypflichtige Tests besitzen Guards | ✅ verifiziert: 48 pass / 2 skip; realer CI-Lauf noch bestätigen | #40 / PR #41 WIP | [Headless-Verifikation](../analysis/exedra-headless-test-verification.md) |
| `ui`-Business-Panels, Commands, Editor-Integration | (in obigem) | — | 🚫 vorerst nicht — erst nach stabilen Use-Case-Ports | bei Bedarf neue kleine Issues | — |

### 2.6 Ohne definiertes Corenth-Ziel — Entscheidung in #44

| research/-Quelle | Umfang | Kandidat | Empfehlung |
| --- | --- | --- | --- |
| `wd4j`, `wd4j-mcp-server`, `wd4j2cdp` (WebDriver BiDi + MCP) | **255** | `katagogion`-Tool/Adapter | ❓ In #44 entscheiden: eigenes Adaptermodul nach #12, externes AresStack-Projekt oder bewusst außerhalb Corenth |
| `mermaid-renderer` | 55 | `exedra`-Renderer oder `katagogion`-Tool | ❓ In #44 dispositionieren; optional, nicht Kern |
| `betaview-original/-integration` | 94 | — | ❓ In #44 voraussichtlich als Forschungsartefakt / nicht migrieren entscheiden |
| `dosbox` | 31 | — | ❓ In #44 voraussichtlich nicht migrieren entscheiden |
| `winml-java`, `onnx` | 24 | `pinakes`-Runtime-Adapter (optional, nie Pflicht) | ❓ In #44 entscheiden; frühestens nach #7-Ports |
| `video` (app) | 6 | `holkas`/`deigma` oder außerhalb | ❓ In #44 konkreten Zuschnitt oder Nichtmigration entscheiden |
| `mermaid-mcp`, PowerShell-Utilities (PAC/WPAD/KeePass-Tests) | — | teils in `platform` erledigt, teils #43 | ❓ In #44 restlos klassifizieren |

#44 ist ein reines Entscheidungs-Issue: Jede Zeile erhält `MIGRATE` mit Ziel + kleinem Folge-Issue, `EXTERNAL` oder `DO_NOT_MIGRATE`. Es wird dort kein Produktionscode implementiert.

---

## 3. Fortschritt Reimplementierungsplan (2026-06-02)

| Plan-PR | Inhalt | Stand |
| --- | --- | --- |
| PR 1 | `proasteion`-Root: OuterAdapter, AdapterKind, AdapterRegistry (#13) | ✅ Zweck erfüllt; #13 geschlossen. Boundary dokumentiert und per ArchUnit erzwungen; gemeinsames Adapter-Vokabular nach YAGNI erst bei konkretem Mehrfachbedarf |
| PR 2 | `holkas` Connector-SPI (#8) | ✅ erledigt; #8 geschlossen, Rest in #34–#39 |
| PR 3 | `emporion` Harbor-Pipeline (#15) | ✅ erledigt; #15 geschlossen |
| PR 4 | `tamias` IndexingPolicy/ChangeDetection/CacheInvalidation (#5) | 🟡 teilweise; #5 auf Restarbeit neu zugeschnitten, abhängig von #33 |
| PR 5 | `acropolis` Run/Plan/Step/Status (#10) | ⬜ offen; produktive Komposition vor Run-Modell; neutraler Bootstrap-Ort vor Implementierung zu dokumentieren; kein Headless-Fix-Blocker |
| PR 6 | FTP/MVS/JES als erster echter Connector | 🔧 FTP/MVS vorhanden; JES separat in #35 |
| PR 7–9 | `pinakes` / `propylaea` / `katagogion` ports-first (#7/#3/#12) | ⬜ offen |

---

## 4. Issue-Hygiene (Stand der Ausführung)

| Issue | Befund | Ausgeführte Aktion |
| ---: | --- | --- |
| #20, #27, #30 | zeichengleiche Duplikate von #18, #24, #28; Deliverables in `main` | als Duplikate geschlossen |
| #8 | Connector-SPI sowie local/FTP/MVS vorhanden; Rest war zu breit gebündelt | geschlossen; ersetzt durch #34–#39 |
| #11 | Thin-Adapter-Regel implementiert, dokumentiert und per ArchUnit erzwungen | geschlossen |
| #15 | Harbor-Boundary implementiert | geschlossen |
| #5 | teilweise erledigt, alte Beschreibung zu breit | auf ChangeDetection/Invalidation/Scope/Depth/Size neu zugeschnitten |
| #10 | Walking Skeleton vorhanden, alte Beschreibung als Erstdefinition veraltet | auf produktive Mediated-Komposition und nachfolgendes Run-Modell neu formuliert; Bootstrap-Modul-Entscheidung ergänzt |
| #13 | Boundary-Regeln vorhanden; reales gemeinsames Adapter-Vokabular bislang nicht benötigt | geschlossen; YAGNI-Entscheidung und Abhängigkeitsrichtungen in `proasteion/README.md` dokumentiert |

### Neue konkrete Issues

| Issue | Thema | Abhängigkeit/Besonderheit |
| ---: | --- | --- |
| #33 | Chalcotheca Resource Records, Version History, Lifecycle Persistence | Voraussetzung für belastbare ChangeDetection/`UNCHANGED` |
| #34 | NDV Connector | `NdvAuthenticationStrategy` + `NdvAccessHandle`, proprietäre Runtime isolieren |
| #35 | JES Submit/Spool | verwendet ausschließlich bestehenden `FtpAccessHandle`; kein zweiter Login |
| #36 | lokale PST/OST-Mail-Ressourcen | kein spekulativer Auth-/Adyton-Pfad; Deigma übernimmt tiefe Extraktion |
| #37 | MediaWiki | Token-Login → wiederverwendbarer Cookie-/Session-Handle |
| #38 | Confluence | getrennte Basic- und Windows-MY-mTLS-Strategien |
| #39 | SharePoint | SSO-first, Credentials/Fallback nur kontrolliert und isoliert |
| #40 | Exedra Headless-CI-Verifikation | nicht blockierend für #10; erwartete Skips sichtbar machen; PR #41 WIP |
| #42 | Deigma-Schwer-Extraktoren | PDF/DOCX/XLSX/HTML/strukturierte Records; schwere Bibliotheken isoliert; vor/parallel zu #36 |
| #43 | Adyton Secret-Source-Adapter | interaktiver Provider zuerst, verschlüsselter Store/DPAPI danach; KeePassRPC bleibt Peer |
| #44 | Research-Disposition | reines Entscheidungs-Issue für §2.6; beeinflusst insbesondere #12/wd4j |

Auffällig: Ab Juni wechselte der Workflow von Copilot-Issue+PR auf Direkt-Commits nach `main` (FTP/MVS, Harbor, platform-Module) — dadurch waren Issues veraltet, ohne geschlossen zu werden. Für künftige Arbeit entweder zum Issue-Workflow zurückkehren oder dieses Inventar als führende Statusquelle pflegen und Issues nur für konkrete nächste Slices anlegen.

---

## 5. Nächste Schritte (konsolidiert)

### Strang A — kritischer Lifecycle-Pfad

1. **#33 und #10 erste Kompositionsslices parallel:** Chalcotheca Resource Records/Persistenz können unabhängig von der Umstellung des `ResourceLifecycleCoordinator` auf `MediatedResourceService`/`AcquisitionPort` beginnen. Vor dem produktiven Bootstrap-Code muss #10 den neutralen Modulort dokumentieren; bevorzugt ein kleines äußeres Application-/Bootstrap-Modul statt `proasteion`-Root oder exklusiver Exedra-Komposition.
2. **`tamias` vervollständigen (#5):** ChangeDetectionStrategy + CacheInvalidationPolicy + Scope/Depth/Size auf Basis der Digests/Versionen aus #33.
3. **Run-/Outcome-Modell (#10, danach):** Run, Plan, Step, Outcome, Summary und Failure extrahieren; `UNCHANGED`/Tombstone mit #33/#5 integrieren.

### Strang B — Extraktion und reale Connectoren

4. **Deigma-Schwer-Extraktoren (#42):** nach #10s erstem produktiven Pfad, vor oder parallel zu Mail; damit PDF/Office/HTML und Attachments nicht am Plaintext-/Markdown-Limit enden.
5. **Connector-Reihenfolge:** Mail (#36) zuerst als risikoarmer lokaler Realtest des vollständigen Lifecycle-Pfads; danach NDV (#34), JES (#35) und getrennt Wiki (#37), Confluence (#38), SharePoint (#39).
6. **Weitere Secret-Quellen (#43):** interaktiven, headless-testbaren Prompt-Provider zuerst; persistente/DPAPI-Adapter danach. Für den ersten authentifizierten #10-Slice reicht vorhandenes KeePassRPC.

### Parallel / nachrangig

7. **Research-Disposition (#44):** §2.6 vollständig auf `MIGRATE`, `EXTERNAL` oder `DO_NOT_MIGRATE` heben; wd4j-Bezug zu #12 ausdrücklich entscheiden.
8. **Exedra-CI-Verifikation (#40 / PR #41, parallel und nicht blockierend):** realen Gradle-/JUnit-Lauf bestätigen und erwartete Skips sichtbar machen. Keine zusätzlichen Guards an leichtgewichtigen Swing-Tests.
9. **#7/#3/#12** bleiben bis nach dem tragfähigen Lifecycle und den priorisierten Adapter-Slices ports-first im Backlog.

Branch-Aufräumarbeiten und die Exedra-CI-Verifikation sind unabhängig von #10 und können parallel erfolgen. Der zuvor angenommene Headless-Codefix entfällt als Blocker. Bei Squash-Merges darf die Löschentscheidung nicht allein auf `git branch --merged` beruhen, sondern auf PR-Merge-Status plus inhaltsbasiertem Vergleich gegen `main`.

---

## 6. Pflegehinweis

Dieses Dokument bei jedem Migrations-PR aktualisieren (Statusspalte + ggf. §3/§4). Modulspezifische Detailentscheidungen gehören weiterhin in die `mainframemate-<modul>-inventory.md`-Dateien; fehlende Inventare (chalcotheca, tamias, emporion, holkas-ftp/mvs, exedra, platform) bei der jeweils nächsten Arbeit am Modul nachziehen.
