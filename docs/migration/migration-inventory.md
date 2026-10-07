# MainframeMate → Corenth — Master-Migrationsinventar

**Stand:** Codeprüfung 2026-07-19 gegen `main` @ `122f999` ("Add trusted MVS session auth adapter", 2026-06-17); Tracker-, Headless- und Lückenbereinigung aktualisiert am 2026-07-19; Session 0 (2026-10-06): #10 Slice 1 umgesetzt, ADR-0001, Produktionsreife-Aussagen zu KeePassRPC, FTP/MVS und CI präzisiert; Nachtintegration 2026-10-07 (lokaler Branch `night/integration`, nicht gemergt): #33, #5, #10 Slices 2–5, #42, #43, #7
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
| `acropolis` ↛ `MediatedResourceService`/`AcquisitionPort`/`holkas` (Lifecycle nur über `MediatedResourceAccess`) | ArchUnit `ACROPOLIS_LIFECYCLE_MUST_ACQUIRE_THROUGH_MEDIATED_ACCESS` | ✅ erzwungen (seit #10 Slice 1) |
| Vollständige Modulabdeckung der Architekturtests | Konfigurations-Guard in `architecture-tests/build.gradle` (nicht gelistetes Projekt bricht den Build) | ✅ erzwungen |
| **Mediated bronze access als Primärpfad** | Lifecycle liest seit #10 Slice 1 ausschließlich über `MediatedResourceAccess` | 🟡 **Lifecycle-Seite umgesetzt; weiterhin nur in Tests komponiert, kein produktiver Kompositionspunkt** |

**Stand Nachtintegration 2026-10-07 (lokal, `night/integration`, noch nicht auf `main`):** Der Lifecycle läuft produktiv über `proasteion:application` (`CorenthComposition.composeLocal`, #10 Slice 2) und beschafft ausschließlich über `MediatedResourceAccess`. Authentifizierungspflichtige Beschaffung geht über den schmalen Port `AcquisitionAccessPort` (Chalcotheca) und die Holkas-Station `BrokeredAcquisitionAccess` auf den Adyton-Broker; lokale Ressourcen lösen keinen Adyton-Aufruf aus, Ablehnung, Abbruch, Freigabeverweigerung und Authentifizierungsfehler bleiben unterscheidbar (Slice 3). Der Lifecycle protokolliert Plan, Schritte, Outcome und typisierte Fehler, ohne Run-Persistenz (Slice 4). Seit Slice 5 arbeitet er direkt auf den #33-Records (`ResourceArchiveRepository`) und führt die #5-Entscheidungen aus (`DigestChangeDetection`, `DerivativeDispositionPolicy`): Refresh bei Tamias-Freigabe `REFRESH_EXTERNAL`, Payload-Invalidierung, Reindex, Index-Withdrawal bei Quellenabwesenheit oder explizitem Tombstone, Größenentscheidung durch die Tamias-`ResourceSizePolicy` vor der Beschaffung über Quellenmetadaten und als Grenze jeder Beschaffung (bei fehlender oder veralteter Größe bricht der Port ein Byte nach dem Limit ab, nichts wird gecacht). Die drei `knownGap_…`-Tests sind durch Regressionstests der jetzt realen Pfade ersetzt. Offen: begrenztes Lesen für authentifizierte Connectoren, H2-Persistenz der Records, produktiver FTP-Transport, Pinakes im Lifecycle. → Nächste Schritte in §5.

---

## 2. Migrationstabelle: research/ → Corenth

### 2.1 Kern & Sicherheit

| research/-Quelle | Umfang | Corenth-Ziel | Status | Issue | Detailinventar |
| --- | --- | --- | --- | --- | --- |
| `core/files/auth`, `app/util` (CredentialStore, SessionCipher, Crypto-Provider) | ~16 | `adyton` | ✅ migriert (Boundary, Broker, Lease, Cache, Strategien) | #1/#14 ✔ | [adyton-inventory](mainframemate-adyton-inventory.md) |
| `app/util/KeePassRpcClient`, `KeePassProvider` (RPC-Teil) | ~3 | `proasteion:platform:security-keepassrpc` | 🟡 teilweise — Adapter-Hülle vorhanden (`KeePassRpcSecretMaterialProvider` implementiert `SecretMaterialProvider`, ArchUnit-vertrauenswürdig), **aber kein bewiesener produktiver Auth-Pfad:** `ReflectiveKeePassRpcSecretLookup` ruft per Reflection `findByRef`/`find`/`get`/`resolve`/`findLogin` auf; die gebündelte `com.aresstack:keepassrpc-java:0.1.0-beta.1` bietet `KeePassRpcCredentialClient.getUserName(String)`/`getPassword(String)` und wird im Modul nirgends importiert. Funktioniert nur gegen Test-Stubs (verifiziert Session 0, `javap` gegen das aufgelöste Jar). Korrektur als eigener kleiner Folge-Slice mit echtem Library-Integrationstest, nicht Teil von #10 | — | — *(Inventar fehlt)* |
| Interactive-Prompt-, DPAPI-/PowerShell-/AES-Secret-Source-Adapter | ~4+ | vertrauenswürdige `proasteion:platform:security-*`-Adapter hinter Adyton-Ports | 🟡 teilweise — interaktiver, headless-testbarer Prompt-Provider umgesetzt (Nacht 2026-10-07, lokal integriert), `SecretReleaseDeniedException` getrennt von Abbruch/Unverfügbarkeit; persistenter Store/DPAPI offen | #43 | [Auth-Analyse §8](../analysis/mainframemate-authentication-flows.md) |
| `KeePassRpcPairingDialog`, `LoginManager`-Swing-Teile | ~3 | — | 🚫 do-not-copy (UI im Vault verboten) | — | adyton-inventory |
| `win-proxy`, Proxy-PS-Skripte | ~10 | `proasteion:platform:network(-winproxy)` | 🟡 teilweise — Routenplanung (Platform-Proxy-/Secure-Gateway-Stufen) und Winproxy-Resolver vorhanden; kein produktiver Transport nutzt einen `NetworkRoutePlan`, Winproxy-Modul ohne Tests | — | — *(Inventar fehlt)* |

### 2.2 Ressourcenmodell, Archiv, Policy, Orchestrierung

| research/-Quelle | Umfang | Corenth-Ziel | Status | Issue | Detailinventar |
| --- | --- | --- | --- | --- | --- |
| `core/files/path` (VirtualResourceRef, PathDialect), `BookmarkEntry`, `ScannedItem` | ~8 | `astu` (BookmarkUri, ResourceScheme, Metadata, Fingerprint) | ✅ migriert | #2/#17 ✔ | [astu-inventory](mainframemate-astu-inventory.md) |
| `app/ui/VirtualResource*` (UI+Backend-State vermischt) | ~4 | — | 🚫 do-not-copy | — | astu-inventory |
| `archive` (CacheRepository, ArchiveRun, Hashing, Snapshots) | 6 | `chalcotheca` (Bronze-Modell, ResourceArchive, MediatedResourceService) | 🟡 teilweise — Resource Records mit Versionshistorie, Indexierungsfakt und Tombstone umgesetzt (In-Memory, Port `ResourceArchiveRepository`, `ResourceArchive` als Kompatibilitätsfassade); Schalter-Caches seit #10 Slice 5 mit Refresh/Invalidierung nach Tamias-Entscheidung (lokal integriert); **H2-Adapter offen, `ResourceArchive`-Fassade bleibt bis dahin für `deleteEntry`** | #33 (Branch) | — *(Inventar fehlt)* |
| `indexing/model/IndexSource` (scope, patterns, depth, size, changeDetection …) | ~7 | `tamias` | 🟡 teilweise — AccessPolicy + `IndexingRule`/Pattern ja; **ChangeDetectionStrategy, CacheInvalidationPolicy, ResourceScope/Depth/Size fehlen** | #5 neu zugeschnitten | — *(Inventar fehlt)* |
| `indexing/service/IndexingPipeline`, `IndexRunStatus` | ~7 | `acropolis` (produktive Mediated-Komposition + Run/Plan/Step/Status) | 🟡 weitgehend — Slices 1–5 umgesetzt (2–5 lokal integriert): produktive Komposition, Access-Preparation-Station, Run/Plan/Step/Outcome/Summary ohne Persistenz, #33/#5-Anwendung, Tamias-Größenpolitik mit begrenzter Beschaffung; offen: begrenztes Lesen für authentifizierte Connectoren, Run-Persistenz nur bei konkretem Bedarf | #10 | [ADR-0001](../adr/0001-composition-root.md), [Plan PR 5](corenth-mainframemate-backend-reimplementation-plan-2026-06-02.md) |
| `indexing/connector/SourceScanner` (scan → fetch → process) | ~4 | `emporion` (ResourceHarbor, HarborRequest/Result/Inspection) | ✅ migriert (vereinfachte Harbor-Pipeline) | #15 geschlossen | — *(Inventar fehlt)* |

### 2.3 Connectors (holkas) & Extraktion (deigma)

| research/-Quelle | Umfang | Corenth-Ziel | Status | Issue | Detailinventar |
| --- | --- | --- | --- | --- | --- |
| `core/files/api` (FileService, FileNode, FilePayload) | ~6 | `holkas` SPI (ResourceConnector[Registry], Listing, ReadMode, RawResource*) | ✅ migriert | #8 geschlossen | [Plan PR 2](corenth-mainframemate-backend-reimplementation-plan-2026-06-02.md) |
| `files/impl/local` | ~4 | `holkas` `FileSystemResourceConnector` | ✅ migriert | #8 geschlossen | — |
| `files/impl/ftp` + MVS (CommonsNet, MvsPathDialect, Listing, QuoteNormalizer) | ~14 | `holkas/ftp` + `holkas/mvs` + `MvsFtpAuthenticationStrategy` (liegt in `proasteion:platform:security-keepassrpc`, nicht in adyton) | 🟡 teilweise — **architektonisch modellierter und gegen Fakes getesteter FTP/MVS-Pfad, aber noch kein produktiver FTP-Transport:** MVS-Adressmodell, `FtpMvsResourceConnector`, `AccessBroker`/`AuthenticationStrategy`, `FtpAccessHandle`, `FtpClientSession`/`FtpClientSessionFactory`/`MvsFtpSessionAuthenticator` existieren nur als Ports plus Test-Fakes; keine Implementierung außerhalb von Tests, kein commons-net in einem Gradle-Modul (verifiziert Session 0). Ein echter Transport ist Voraussetzung für #35 und für jeden Live-Test | #8 geschlossen; Transport-Slice fehlt als Issue | — *(Inventar fehlt — anlegen, dient als Vorlage für NDV)* |
| `files/impl/ftp/jes` (JES Submit/Spool) | ~3 | `holkas` (JES über vorhandenen `FtpAccessHandle`) | ⬜ offen | #35 | Auth-Analyse §2/§8 |
| `ndv/**`, `files/impl/ndv` | **172** | `holkas`-Adapter + adyton `NdvAuthenticationStrategy`/`NdvAccessHandle` | ⬜ offen — größter unmigrierter Connector | #34 | Auth-Analyse §2/§8 |
| `mail` (lokale PST/OST) | 6 | `holkas` mail + `deigma` Attachments | ⬜ offen — bewusst ohne spekulativen Auth-Pfad | #36 | deigma-inventory |
| `wiki-integration`, `wiki` | Teil von ~28 | `holkas` MediaWiki + Token-/Cookie-Session-Handle | ⬜ offen | #37 | Auth-Analyse §2/§4/§8 |
| `confluence` | Teil von ~28 | `holkas` Confluence + Basic-/mTLS-Strategien | ⬜ offen | #38 | Auth-Analyse §2/§4/§8 |
| `sharepoint` | Teil von ~28 | `holkas` SharePoint + SSO-Kaskade/kontrollierter Fallback | ⬜ offen | #39 | Auth-Analyse §2/§4/§8 |
| `ingestion` (Detector, Registry, Document/Block, PlainText/Markdown) | 11 | `deigma` | ✅ migriert (Kern) | #9/#22 ✔ | [deigma-inventory](mainframemate-deigma-inventory.md) |
| `ingestion`-Schwer-Extraktoren (PDF/DOCX/XLSX/HTML/Tika), `RecordStructureCodec` | ~7 | isolierte `deigma`-Implementierungsadapter | 🟡 teilweise — HTML (JSoup), PDF (PDFBox), DOCX/XLSX (POI) als isolierte `deigma-*`-Module, im Composition Root registriert und bis in die lexikalische Suche getestet (lokal integriert); strukturierte Records/`RecordStructureCodec` offen | #42 | deigma-inventory |

### 2.4 Indizes & Analyse

| research/-Quelle | Umfang | Corenth-Ziel | Status | Issue | Detailinventar |
| --- | --- | --- | --- | --- | --- |
| `rag` lexikalisch (LuceneLexicalIndex, Chunk, PR-#51-Chunking) | ~10 | `anagraphai` (+`chunking`) | ✅ migriert | #6/#21, #24/#25 ✔ | [anagraphai-inventory](mainframemate-anagraphai-inventory.md) |
| `rag` semantisch (SemanticIndex, EmbeddingClient, HybridRetriever, Reranker) | ~10 | `pinakes` (ports-first) | 🟡 teilweise — Ports, Werte, deterministischer In-Memory-Referenzindex, RRF und Hybrid Retrieval mit getrennt schaltbaren Stufen (lokal integriert, ArchUnit-Regel `PINAKES_MUST_STAY_SEMANTIC_REGISTER`); reale Embedding/Rerank-Runtime, persistenter Index und Lifecycle-Verdrahtung offen | #7 | [Plan PR 7](corenth-mainframemate-backend-reimplementation-plan-2026-06-02.md) |
| `jcl` (ANTLR-Grammatiken), `service/codeanalytics` (Natural/COBOL/DDM-Parser, CallExtractor) | ~15 | `propylaea` (Model-first, Parser als Adapter) | 🟡 teilweise — sprachneutrales Strukturmodell, Parser-Port, Registry und Parser für Natural, JCL und COBOL (fixture-getestet, lokal integriert, ArchUnit `PROPYLAEA_MUST_STAY_A_PURE_SOURCE_GATE`); Application-Verdrahtung und Tests gegen echte Quellen offen | #3 | [Plan PR 8](corenth-mainframemate-backend-reimplementation-plan-2026-06-02.md) |
| `mcp`, `runtime`, `plugins` (ToolSpec, ToolRegistry, PluginManager) | ~35 | `katagogion` (ports-first, Tools nur über Mediated Ports) | 🟡 teilweise — expliziter Tool-Host mit Allowlist-Zulassung, ServiceLoader-Discovery ohne Installation, Referenztools Suche/Lesen; Application bindet Suche und Mediated Access (Tamias entscheidet jeden Tool-Lesezugriff), ArchUnit `KATAGOGION_TOOLS_MUST_STAY_ON_MEDIATED_CAPABILITIES` (lokal integriert); MCP, wd4j und Prozessisolation offen | #12 | [Plan PR 9](corenth-mainframemate-backend-reimplementation-plan-2026-06-02.md) |

### 2.5 UI

| research/-Quelle | Umfang | Corenth-Ziel | Status | Issue | Detailinventar |
| --- | --- | --- | --- | --- | --- |
| `ui`-Shell (MainFrame, Drawer, ToolTabRegistry, Settings-Shell), `toolbar-kit`, `event` | ~254 | `exedra` (generisches Shell-Framework) | ✅ migriert — **eingefroren**, Business-Panels bewusst nicht | #28/#29 ✔, #30 geschlossen | exedra/README |
| Thin-Adapter-Grenze für Business-UI | — | `exedra` bleibt austauschbarer Adapter; `EXEDRA_MUST_STAY_THIN_UI_SHELL` | ✅ dokumentiert und erzwungen | #11 geschlossen | Plan „Korrektur zu #30/#11“ |
| Exedra Headless-Tests | 50 Testfälle | leichtgewichtige Swing-Tests laufen headless; zwei displaypflichtige Tests besitzen Guards | ✅ verifiziert: 48 pass / 2 skip, durch CI-Lauf 8 auf `main` und Lauf 9 auf PR #46 (`pull_request`) bestätigt, Skips namentlich in der Summary | #40 erfüllt; PR #41 redundant, wird nicht gemergt | [Headless-Verifikation](../analysis/exedra-headless-test-verification.md), [CI-Stand](../analysis/ci-build-test-gap.md) |
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
| PR 4 | `tamias` IndexingPolicy/ChangeDetection/CacheInvalidation (#5) | 🟡 Scope/Traversal/Size, ChangeDetection und Derivative Disposition umgesetzt und vom Lifecycle angewandt (lokal integriert) |
| PR 5 | `acropolis` Run/Plan/Step/Status (#10) | 🟡 Slices 1–5 umgesetzt (2–5 lokal integriert); begrenztes Lesen für authentifizierte Connectoren offen |
| PR 6 | FTP/MVS/JES als erster echter Connector | 🟡 FTP/MVS nur als Ports, `FtpAccessHandle` und Test-Fakes modelliert, kein produktiver FTP-Transport (s. §2.3); JES separat in #35, setzt den Transport-Slice voraus |
| PR 7–9 | `pinakes` / `propylaea` / `katagogion` ports-first (#7/#3/#12) | 🟡 Kerne umgesetzt und lokal integriert (Nacht 2026-10-07); reale Runtimes, Application-Verdrahtung von Propylaea/Pinakes und MCP offen |

---

## 4. Issue-Hygiene (Stand der Ausführung)

| Issue | Befund | Ausgeführte Aktion |
| ---: | --- | --- |
| #20, #27, #30 | zeichengleiche Duplikate von #18, #24, #28; Deliverables in `main` | als Duplikate geschlossen |
| #8 | Connector-SPI und `file:`-Connector vorhanden; FTP/MVS nur als Ports plus Test-Fakes ohne produktiven Transport (§2.3); Rest war zu breit gebündelt | geschlossen; ersetzt durch #34–#39 — der FTP-Transport-Slice ist dabei durch kein Issue abgedeckt und muss separat angelegt werden |
| #11 | Thin-Adapter-Regel implementiert, dokumentiert und per ArchUnit erzwungen | geschlossen |
| #15 | Harbor-Boundary implementiert | geschlossen |
| #5 | teilweise erledigt, alte Beschreibung zu breit | auf ChangeDetection/Invalidation/Scope/Depth/Size neu zugeschnitten |
| #10 | Walking Skeleton vorhanden, alte Beschreibung als Erstdefinition veraltet | auf produktive Mediated-Komposition und nachfolgendes Run-Modell neu formuliert; Bootstrap-Modul-Entscheidung ergänzt |
| #13 | Boundary-Regeln vorhanden; reales gemeinsames Adapter-Vokabular bislang nicht benötigt | geschlossen; YAGNI-Entscheidung und Abhängigkeitsrichtungen in `proasteion/README.md` dokumentiert |

### Neue konkrete Issues

| Issue | Thema | Abhängigkeit/Besonderheit |
| ---: | --- | --- |
| #33 | Chalcotheca Resource Records, Version History, Lifecycle Persistence | Voraussetzung für belastbare ChangeDetection/`UNCHANGED`; Session 1: Fakten-Modell ohne lineare State Machine und ohne persistierte DENIED/FAILED-Outcomes umgesetzt (Kompendium Kapitel 3) |
| #34 | NDV Connector | `NdvAuthenticationStrategy` + `NdvAccessHandle`, proprietäre Runtime isolieren |
| #35 | JES Submit/Spool | verwendet ausschließlich bestehenden `FtpAccessHandle`; kein zweiter Login |
| #36 | lokale PST/OST-Mail-Ressourcen | kein spekulativer Auth-/Adyton-Pfad; Deigma übernimmt tiefe Extraktion |
| #37 | MediaWiki | Token-Login → wiederverwendbarer Cookie-/Session-Handle |
| #38 | Confluence | getrennte Basic- und Windows-MY-mTLS-Strategien |
| #39 | SharePoint | SSO-first, Credentials/Fallback nur kontrolliert und isoliert |
| #40 | Exedra Headless-CI-Verifikation | nicht blockierend für #10; Skips sind seit `build.yml` in der Summary sichtbar; PR #41 redundant (nicht mergen) |
| #42 | Deigma-Schwer-Extraktoren | PDF/DOCX/XLSX/HTML/strukturierte Records; schwere Bibliotheken isoliert; vor/parallel zu #36 |
| #43 | Adyton Secret-Source-Adapter | interaktiver Provider zuerst, verschlüsselter Store/DPAPI danach; KeePassRPC bleibt Peer |
| #44 | Research-Disposition | reines Entscheidungs-Issue für §2.6; beeinflusst insbesondere #12/wd4j |

Auffällig: Ab Juni wechselte der Workflow von Copilot-Issue+PR auf Direkt-Commits nach `main` (FTP/MVS, Harbor, platform-Module) — dadurch waren Issues veraltet, ohne geschlossen zu werden. Für künftige Arbeit entweder zum Issue-Workflow zurückkehren oder dieses Inventar als führende Statusquelle pflegen und Issues nur für konkrete nächste Slices anlegen.

---

## 5. Nächste Schritte (konsolidiert)

### Strang A — kritischer Lifecycle-Pfad

1. **#33 und #10 Slice 2 parallel:** Die Umstellung des `ResourceLifecycleCoordinator` auf `MediatedResourceAccess` (#10 Slice 1) und die Entscheidung über den Bootstrap-Ort ([ADR-0001](../adr/0001-composition-root.md): `proasteion:application`) sind erledigt. Chalcotheca Resource Records/Persistenz (#33) und der produktive Kompositionspunkt (#10 Slice 2) können nun unabhängig voneinander beginnen; Slice 2 muss die erste produktive `ResourceAccessPolicy` explizit wählen und das neue Modul in die ArchUnit-Abdeckung aufnehmen. Die Cache-Konsolidierung des Schalters erfolgt erst gegen den #33-Vertrag.
2. **`tamias` vervollständigen (#5):** ChangeDetectionStrategy + CacheInvalidationPolicy + Scope/Depth/Size + Withdrawal-Entscheidung auf Basis der #33-Fakten (beobachtete Versionen, indexierte Version, Tombstone). Tamias liest die Fakten als Eingabe, persistiert aber keine Entscheidungen im Record.
3. **Run-/Outcome-Modell (#10):** umgesetzt (Slices 4 und 5, lokal integriert); der Lifecycle arbeitet direkt auf `ResourceArchiveRepository`. Nächste kleine Pakete: begrenztes Lesen für authentifizierte Connectoren (FTP-Session als Stream), H2-Adapter für `ResourceArchiveRepository`, danach `ResourceArchive`-Fassade nur noch dort behalten, wo `deleteEntry` sie braucht, oder durch einen schmalen Tombstone-Port ersetzen.

### Strang B — Extraktion und reale Connectoren

4. **Deigma-Schwer-Extraktoren (#42):** nach #10s erstem produktiven Pfad, vor oder parallel zu Mail; damit PDF/Office/HTML und Attachments nicht am Plaintext-/Markdown-Limit enden.
5. **Connector-Reihenfolge:** Mail (#36) zuerst als risikoarmer lokaler Realtest des vollständigen Lifecycle-Pfads; danach NDV (#34), JES (#35) und getrennt Wiki (#37), Confluence (#38), SharePoint (#39).
6. **Weitere Secret-Quellen (#43):** interaktiven, headless-testbaren Prompt-Provider zuerst; persistente/DPAPI-Adapter danach. Für den ersten authentifizierten #10-Slice (Slice 3) reicht das vorhandene KeePassRPC **nicht** ohne Weiteres: seine Lookup-Bindung an `keepassrpc-java` ist nicht funktionsfähig (§2.1). Entweder diese Bindung in einem kleinen Folge-Slice mit Library-Integrationstest korrigieren oder den #43-Prompt-Provider als ersten authentifizierten Pfad nutzen.

### Parallel / nachrangig

7. **Research-Disposition (#44):** §2.6 vollständig auf `MIGRATE`, `EXTERNAL` oder `DO_NOT_MIGRATE` heben; wd4j-Bezug zu #12 ausdrücklich entscheiden.
8. **CI als Merge-Gate (#45 / #40):** PR-Lauf nachgewiesen (PR #46, Lauf 9: 394 Tests, 0 Fehler, 2 Skips); jetzt Required Check `Build and test (Java 8 target)` im Ruleset aktivieren, #41 ohne Merge schließen, #40 schließen. Keine zusätzlichen Guards an leichtgewichtigen Swing-Tests. Stand und Begründung: `docs/analysis/ci-build-test-gap.md`.
9. **#7/#3/#12:** Kerne liegen lokal integriert vor. Nächste kleine Pakete: Pinakes-Hook im Lifecycle (`replaceResource`/`removeResource` mit denselben `LexicalChunk`s), Propylaea gegen echte Quellen, Katagogion-MCP-Adapter erst nach #44.

Branch-Aufräumarbeiten und die Exedra-CI-Verifikation sind unabhängig von #10 und können parallel erfolgen. Der zuvor angenommene Headless-Codefix entfällt als Blocker. Bei Squash-Merges darf die Löschentscheidung nicht allein auf `git branch --merged` beruhen, sondern auf PR-Merge-Status plus inhaltsbasiertem Vergleich gegen `main`.

---

## 6. Pflegehinweis

Dieses Dokument bei jedem Migrations-PR aktualisieren (Statusspalte + ggf. §3/§4). Modulspezifische Detailentscheidungen gehören weiterhin in die `mainframemate-<modul>-inventory.md`-Dateien; fehlende Inventare (chalcotheca, tamias, emporion, holkas-ftp/mvs, exedra, platform) bei der jeweils nächsten Arbeit am Modul nachziehen.
