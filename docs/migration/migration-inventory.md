# MainframeMate → Corenth — Master-Migrationsinventar

**Stand:** 2026-07-19, gegen `main` @ `122f999` ("Add trusted MVS session auth adapter", 2026-06-17)
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

**Zentrale Lücke:** Es fehlt keine Klasse der Kette, sondern die produktive Komposition. `MediatedResourceService` (Chalcotheca-Schalter) und `ResourceLifecycleCoordinator` (Indexing-Skeleton) existieren beide, werden aber ausschließlich von Tests instanziiert (`MediatedHolkasFileSliceTest`, `WalkingSkeletonIntegrationTest`). Zudem fehlt im `MediatedResourceService` die im Datenfluss dokumentierte adyton-Station („Adyton, if credentials … are needed"). → Nächste Schritte in §5.

---

## 2. Migrationstabelle: research/ → Corenth

### 2.1 Kern & Sicherheit

| research/-Quelle | Umfang | Corenth-Ziel | Status | Issue | Detailinventar |
| --- | --- | --- | --- | --- | --- |
| `core/files/auth`, `app/util` (CredentialStore, SessionCipher, Crypto-Provider) | ~16 | `adyton` | ✅ migriert (Boundary, Broker, Lease, Cache, Strategien) | #1/#14 ✔ | [adyton-inventory](mainframemate-adyton-inventory.md) |
| `app/util/KeePassRpcClient`, `KeePassProvider` (RPC-Teil) | ~3 | `proasteion:platform:security-keepassrpc` | ✅ migriert (`KeePassRpcSecretMaterialProvider`) | — | — *(Inventar fehlt)* |
| DPAPI-/PowerShell-/AES-Crypto-Adapter | ~4 | künftige `adyton-*`-Adaptermodule | ⬜ offen (per Analyse bewusst verschoben) | — | [Auth-Analyse §8](../analysis/mainframemate-authentication-flows.md) |
| `KeePassRpcPairingDialog`, `LoginManager`-Swing-Teile | ~3 | — | 🚫 do-not-copy (UI im Vault verboten) | — | adyton-inventory |
| `win-proxy`, Proxy-PS-Skripte | ~10 | `proasteion:platform:network(-winproxy)` | ✅ migriert | — | — *(Inventar fehlt)* |

### 2.2 Ressourcenmodell, Archiv, Policy, Orchestrierung

| research/-Quelle | Umfang | Corenth-Ziel | Status | Issue | Detailinventar |
| --- | --- | --- | --- | --- | --- |
| `core/files/path` (VirtualResourceRef, PathDialect), `BookmarkEntry`, `ScannedItem` | ~8 | `astu` (BookmarkUri, ResourceScheme, Metadata, Fingerprint) | ✅ migriert | #2/#17 ✔ | [astu-inventory](mainframemate-astu-inventory.md) |
| `app/ui/VirtualResource*` (UI+Backend-State vermischt) | ~4 | — | 🚫 do-not-copy | — | astu-inventory |
| `archive` (CacheRepository, ArchiveRun, Hashing, Snapshots) | 6 | `chalcotheca` (Bronze-Modell, ResourceArchive, MediatedResourceService) | 🟡 teilweise — In-Memory ja; **Persistenz (H2), Versionierung, Lifecycle-State fehlen** | #4 ✔ / Rest offen | — *(Inventar fehlt)* |
| `indexing/model/IndexSource` (scope, patterns, depth, size, changeDetection …) | ~7 | `tamias` | 🟡 teilweise — AccessPolicy + `IndexingRule`/Pattern ja; **ChangeDetectionStrategy, CacheInvalidationPolicy, ResourceScope/Depth/Size fehlen** | #5 offen | — *(Inventar fehlt)* |
| `indexing/service/IndexingPipeline`, `IndexRunStatus` | ~7 | `acropolis` (Run/Plan/Step/Status-Modell) | ⬜ offen — Coordinator ist monolithischer Walking Skeleton; Run-Modell fehlt | #10 offen | [Plan PR 5](corenth-mainframemate-backend-reimplementation-plan-2026-06-02.md) |
| `indexing/connector/SourceScanner` (scan → fetch → process) | ~4 | `emporion` (ResourceHarbor, HarborRequest/Result/Inspection) | ✅ migriert (vereinfachte Harbor-Pipeline) | #15 offen → schließbar | — *(Inventar fehlt)* |

### 2.3 Connectors (holkas) & Extraktion (deigma)

| research/-Quelle | Umfang | Corenth-Ziel | Status | Issue | Detailinventar |
| --- | --- | --- | --- | --- | --- |
| `core/files/api` (FileService, FileNode, FilePayload) | ~6 | `holkas` SPI (ResourceConnector[Registry], Listing, ReadMode, RawResource*) | ✅ migriert | #8 offen → teilschließbar | [Plan PR 2](corenth-mainframemate-backend-reimplementation-plan-2026-06-02.md) |
| `files/impl/local` | ~4 | `holkas` `FileSystemResourceConnector` | ✅ migriert | #8 | — |
| `files/impl/ftp` + MVS (CommonsNet, MvsPathDialect, Listing, QuoteNormalizer) | ~14 | `holkas/ftp` + `holkas/mvs` + adyton-Strategie (`MvsFtpAuthenticationStrategy`) | 🔧 in Arbeit — Session/AccessHandle/Dialekt vorhanden, jüngster Commit bindet Trusted-Session-Auth an | #8 | — *(Inventar fehlt — anlegen, dient als Vorlage für NDV)* |
| `files/impl/ftp/jes` (JES Submit/Spool) | ~3 | `holkas` (JES auf FTP-AccessHandle) | ⬜ offen | #8 | Auth-Analyse §2 |
| `ndv/**`, `files/impl/ndv` | **172** | `holkas`-Adapter + adyton `NdvAuthenticationStrategy` | ⬜ offen — größter unmigrierter Connector | #8 | Auth-Analyse §2 (Session-Handle-Muster vorgezeichnet) |
| `mail` (PST/OST) | 6 | `holkas` mail + `deigma` Attachments | ⬜ offen | #8 | deigma-inventory (Attachment-Teil) |
| `wiki-integration`, `wiki`, `confluence`, `sharepoint` | ~28 | `holkas`-Adapter + adyton-Strategien (Cookie/Header/SSLContext als AccessHandle) | ⬜ offen | #8 | Auth-Analyse §2/§4 |
| `ingestion` (Detector, Registry, Document/Block, PlainText/Markdown) | 11 | `deigma` | ✅ migriert (Kern) | #9/#22 ✔ | [deigma-inventory](mainframemate-deigma-inventory.md) |
| `ingestion`-Schwer-Extraktoren (PDF/DOCX/XLSX/HTML/Tika), `RecordStructureCodec` | ~7 | `deigma:impl` (isoliert) | ⬜ offen (dokumentiert verschoben) | — | deigma-inventory |

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
| `ui`-Business-Panels, Commands, Editor-Integration | (in obigem) | — | 🚫 vorerst nicht — erst nach stabilen Use-Case-Ports | #11 (Thin-Adapter-Regel) | Plan „Korrektur zu #30/#11" |

### 2.6 Ohne definiertes Corenth-Ziel — Entscheidung ausstehend

| research/-Quelle | Umfang | Kandidat | Empfehlung |
| --- | --- | --- | --- |
| `wd4j`, `wd4j-mcp-server`, `wd4j2cdp` (WebDriver BiDi + MCP) | **255** | `katagogion`-Tool/Adapter | ❓ Entscheiden: eigenes Adaptermodul nach #12, oder explizit außerhalb Corenth belassen |
| `mermaid-renderer` | 55 | `exedra`-Renderer oder `katagogion`-Tool | ❓ vermutlich später; nicht Kern |
| `betaview-original/-integration` | 94 | — | ❓ vermutlich reines Forschungsartefakt → als „nicht migrieren" markieren |
| `dosbox` | 31 | — | ❓ vermutlich nicht migrieren |
| `winml-java`, `onnx` | 24 | `pinakes`-Runtime-Adapter (optional, nie Pflicht) | ❓ erst nach #7-Ports relevant |
| `video` (app) | 6 | `holkas`/`deigma`? | ❓ klären |
| `mermaid-mcp`, PowerShell-Utilities (PAC/WPAD/KeePass-Tests) | — | teils in `platform` erledigt | ❓ Rest inventarisieren |

*Jede ❓-Zeile sollte per kurzem Beschluss auf „Ziel + Issue" oder „🚫 nicht migrieren" gehoben werden — sonst stolpert jede künftige Planung erneut über diese Bestände.*

---

## 3. Fortschritt Reimplementierungsplan (2026-06-02)

| Plan-PR | Inhalt | Stand |
| --- | --- | --- |
| PR 1 | `proasteion`-Root: OuterAdapter, AdapterKind, AdapterRegistry (#13) | ⬜ offen — Root weiterhin 0 Klassen (Dependency-Regeln immerhin via ArchUnit abgedeckt) |
| PR 2 | `holkas` Connector-SPI (#8) | ✅ erledigt |
| PR 3 | `emporion` Harbor-Pipeline (#15) | ✅ erledigt (vereinfacht) |
| PR 4 | `tamias` IndexingPolicy/ChangeDetection/CacheInvalidation (#5) | 🟡 teilweise (AccessPolicy ✓, Rest offen) |
| PR 5 | `acropolis` Run/Plan/Step/Status (#10) | ⬜ offen |
| PR 6 | FTP/MVS/JES als erster echter Connector | 🔧 in Arbeit (FTP/MVS ✓ inkl. adyton-Strategie; JES offen) |
| PR 7–9 | `pinakes` / `propylaea` / `katagogion` ports-first (#7/#3/#12) | ⬜ offen |

---

## 4. Issue-Hygiene (Stand der Prüfung)

| Issue | Befund | Aktion |
| ---: | --- | --- |
| #20, #27, #30 | zeichengleiche Duplikate von #18, #24, #28; Deliverables in `main` | als Duplikate geschlossen |
| #8 (holkas) | SPI + file/ftp/mvs implementiert; NDV/mail/wiki/JES offen | schließen und pro Connector neue, kleine Issues anlegen |
| #15 (emporion) | Harbor implementiert | schließen |
| #5, #10, #13, #7, #3, #12, #11 | echte offene Arbeit gemäß Plan | offen lassen, Reihenfolge s. §5 |

---

## 5. Nächste Schritte (konsolidiert)

1. **CI-Hygiene:** `exedra`-Swing-Tests headless-fähig machen (`GraphicsEnvironment.isHeadless()`-Assumes) — sonst ist die Suite als Regressionsnetz auf Linux/CI entwertet.
2. **Produktiver Primärpfad (#10-Vorstufe):** `ResourceLifecycleCoordinator` auf `MediatedResourceService`/`AcquisitionPort` als einzige Beschaffungsquelle umstellen und adyton-Station im `MediatedResourceService` ergänzen; erst danach Run/Plan/Step-Modell extrahieren (klein: Run, Outcome, Summary, Failure).
3. **`tamias` vervollständigen (#5):** ChangeDetectionStrategy + CacheInvalidationPolicy — Voraussetzung dafür, dass ein `UNCHANGED`-Outcome im Run-Modell überhaupt testbar ist (benötigt Digest/Version in `chalcotheca`).
4. **`chalcotheca`-Persistenz:** BronzeResourceRecord + Versionierung (H2 später als Infra-Adapter).
5. **NDV-Connector:** nach dem FTP/MVS-Muster (`AccessHandle` + `AuthenticationStrategy`); FTP-Slice als Vorlage nutzen.
6. **§2.6-Entscheidungen** treffen und hier nachtragen.

---

## 6. Pflegehinweis

Dieses Dokument bei jedem Migrations-PR aktualisieren (Statusspalte + ggf. §3/§4). Modulspezifische Detailentscheidungen gehören weiterhin in die `mainframemate-<modul>-inventory.md`-Dateien; fehlende Inventare (chalcotheca, tamias, emporion, holkas-ftp/mvs, exedra, platform) bei der jeweils nächsten Arbeit am Modul nachziehen.
