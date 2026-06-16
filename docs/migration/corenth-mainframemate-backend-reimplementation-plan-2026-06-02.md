# Corenth Backend-Reimplementierung aus MainframeMate-Erkenntnissen

**Stand:** 2026-06-02  
**Corenth-Quelle:** `/mnt/data/corenth-main.zip`  
**MainframeMate-Quelle:** `/mnt/data/MainframeMate-master(52).zip`

## Kernaussage

Corenth sollte MainframeMate nicht portieren, sondern die fachlich belastbaren Erkenntnisse sauber neu schneiden. Der hochgeladene MainframeMate-Stand entspricht den Java-Quellen, die im aktuellen Corenth-Archiv bereits unter `research/` liegen. Damit kann `research/` als dauerhaft verfügbare Referenz dienen, aber nicht als Zielarchitektur.

Die wichtigste Konsequenz lautet:

```text
MainframeMate-Code = Beleg und Erfahrungsquelle
Corenth-Code      = saubere Reimplementierung entlang der neuen Modulgrenzen
```

Besonders wichtig sind dabei die bereits bewusst neu geschnittenen Grenzen:

```text
adyton       = Authentifizierung, Secret Boundary, AccessBroker, AccessHandle
astu         = inneres Modell, Resource Identity, Core Contracts
acropolis    = Core-Orchestrierung um Archive, Policy und Indizes
chalcotheca  = Resource Archive / Cache / Lifecycle-State
anagraphai   = lexikalischer Index
pinakes      = semantischer Index, Embeddings, Reranking
propylaea    = tiefe Source-Code-Analyse
proasteion   = äußerer Adapterring
emporion     = Datenadapter-Hafen
holkas       = rohe externe Ressourcen / Transport
 deigma      = flache Content-Erkennung und Extraktion
exedra       = konkrete lokale Swing-UI
katagogion   = Plugins, Tools, MCP und Adapter-Lodging
```

## Wichtige Korrektur zu #30 und #11

`#30` ist nicht als UI-neutrale Abstraktion zu behandeln. `exedra` ist die konkrete Swing-Shell und darf Swing-Typen verwenden. Austauschbarkeit entsteht nicht durch UI-Neutralität innerhalb von `exedra`, sondern dadurch, dass kein Core-Modul von `exedra` abhängt.

```text
exedra-swing  ->  Use-Case-/Application-Ports  ->  astu/acropolis/chalcotheca/...
```

Nicht erlaubt:

```text
astu/acropolis/chalcotheca/...  ->  exedra
```

`#11` sollte deshalb als Thin-Adapter-Regel verstanden werden:

```text
Exedra darf konkrete Swing-UI sein.
Exedra darf aber keine fachliche Pipeline, keine Connector-Entscheidung,
keine Policy-Entscheidung und keine Indexierungslogik besitzen.
```

Die UI löst nur Anwendungsfälle aus und zeigt Ergebnisse an.

## Bereits vorhandener Corenth-Stand

| Bereich | Aktueller Stand | Einschätzung |
|---|---|---|
| `adyton` | AccessBroker, AccessRequest, AccessGrant, AccessHandle, CredentialLease, SecretMaterialCache, AuthenticationStrategy vorhanden | Konzeptuell stark. Als Grundlage für alle echten Connectoren verwenden. |
| `astu` | BookmarkUri, ResourceScheme, VirtualResourceRef, VirtualResourceMetadata, Fingerprint/ContentRef vorhanden | Die MainframeMate-`VirtualResource` wurde richtigerweise nicht kopiert. |
| `deigma` | ContentDetector, ExtractionRegistry, ExtractedDocument/Block, PlainText/Markdown-Extractor vorhanden | Gute flache Extraktionsgrenze. Optional später Tika/PDF/Office-Adapter. |
| `holkas` | `file:` Connector und RawResource-Modell vorhanden | Noch zu klein. Registry, Listing und authentifizierte Connectoren fehlen. |
| `anagraphai` | Lucene-Index, LexicalDocument, LexicalChunk, OpenNLP/BreakIterator-Chunking vorhanden | #27 ist weitgehend erfüllt. |
| `chalcotheca` | Bronze-Modelle, ResourceArchive, MediatedResourceService vorhanden | Gute Richtung: Zugriff über Tamias vermitteln, nicht direkt auf Connectoren. |
| `tamias` | ResourcePolicy/AccessPolicy, PatternResourcePolicy, Reason-Codes teilweise vorhanden | Muss um IndexingPolicy, ChangeDetection und CacheInvalidation erweitert werden. |
| `acropolis` | ResourceLifecycleCoordinator und Walking-Skeleton vorhanden | Funktioniert als Start, sollte aber in Run/Plan/Step-Modell zerlegt werden. |
| `proasteion` | Root-Modul leer | Gemeinsame Adapterbegriffe und Dependency-Regeln fehlen. |
| `emporion` | Koordinationsmodul leer | Holkas+Deigma-Harbor-Pipeline fehlt. |
| `katagogion` | leer | Plugin-/Tool-/MCP-Ports fehlen. |
| `propylaea` | leer | Source-Code-Modell und Parser-Ports fehlen. |
| `pinakes` | leer | Semantic-Index-Ports fehlen. |
| `exedra` | Swing-Shell-Framework vorhanden | #30 eher schließen/einfrieren, UI nicht weiter priorisieren. |

## Migrationsprinzipien

### 1. Erkenntnisse übernehmen, Kopplungen nicht

MainframeMate enthält viele belastbare Erkenntnisse, aber auch viele historisch gewachsene Kopplungen:

```text
SettingsHelper, globale Singletons, UI-Zustand, direkte Passwortobjekte,
monolithische Services, SwingWorker-Progress, application folder assumptions
```

Diese Dinge werden nicht portiert. Sie werden als Hinweise für Corenth-Ports und Adapter verwendet.

### 2. Authentifizierung bleibt im neuen Modell

MainframeMate hat raw credentials breit durchgereicht:

```text
Credentials.getPassword()
CredentialStore.resolve() -> String[]{user, password}
FTPClient.login(user, password)
NdvService.connect(host, port, user, password)
new String(credentials.password())
Authorization header construction
```

Corenth darf das nicht wieder einführen. Connectoren erhalten keine allgemeinen Passwortgetter. Sie arbeiten über:

```text
AccessBroker
AuthenticationStrategy
AccessHandle
AccessGrant
CredentialLease
```

Echter FTP/JES/NDV/Wiki/Confluence-Code darf raw secret material nur innerhalb einer konkreten `AuthenticationStrategy` sehen.

### 3. Externe Daten laufen durch Ports

MainframeMate hatte `VirtualResource` als Idee, aber mit UI- und Backend-State vermischt. Corenth hat das richtiger geschnitten:

```text
BookmarkUri / VirtualResourceRef  = stabile Identität
holkas                            = rohes Beschaffen
 deigma                            = flaches Erkennen/Extrahieren
acropolis/chalcotheca             = Lifecycle, Cache, Policy, Index-Ableitung
```

Die UI, Plugins und Tools sprechen nicht direkt mit Holkas-Connectoren. Primärpfad:

```text
exedra / katagogion / application use case
  -> acropolis / chalcotheca mediated access
  -> tamias policy decision
  -> adyton access if needed
  -> holkas acquisition internally
  -> deigma extraction
  -> chalcotheca snapshot/cache
  -> anagraphai / pinakes / propylaea derived outputs
```

### 4. Kein Backend-Fortschritt durch UI-Arbeit blockieren

`#30` liefert genug Shell-Framework: Main Window, Menü, Shortcuts, Toolbar, Toolwindows, Settings Shell. Die nächsten Tickets sollten Backend-Struktur stabilisieren.

## MainframeMate-Quellbereiche und Corenth-Zielmodule

| MainframeMate-Bereich | Wichtige Klassen/Konzepte | Corenth-Ziel | Entscheidung |
|---|---|---|---|
| `core/files/api` | `FileService`, `FileNode`, `FilePayload`, `FileWriteResult` | `holkas` + evtl. `chalcotheca` | Konzept adaptieren, aber auf ResourceConnector/RawResource/Listing splitten. |
| `core/files/path` | `VirtualResourceRef`, `PathDialect`, `MvsPathDialect` | `astu` + `holkas` | Identity liegt in `astu`; MVS-Pfade als Holkas-spezifische Dialect/Locator-Logik. |
| `app/ui/VirtualResource*` | Resource mit UI/Backend-State | nicht kopieren | Bereits korrekt durch Corenth-Resource-Modelle ersetzt. |
| `files/impl/local` | Local file operations | `holkas` | `file:` Connector weiter ausbauen, Listing ergänzen. |
| `files/impl/ftp` | CommonsNet FTP, binary/text, MVS mode, record structure | `holkas` + `adyton` | In kleine Adapterklassen zerlegen, keine SettingsHelper/Passwortweitergabe. |
| `files/impl/ftp/jes` | JES Submit/List Spool/Read Spool | `holkas` + optional eigener JES connector | Mit FTP AccessHandle wiederverwenden, JES darf nicht selbst Credentials auflösen. |
| `ndv`, `files/impl/ndv`, `files/impl/vfs/ndv` | NDV/Natural Development Server | `holkas` optional adapter + `adyton` strategy | Nicht blind kopieren. Sessionhandle kapseln, raw password nie in Resource-Modellen. |
| `mail` + `indexing/connector/MailSourceScanner` | PST/OST Discovery und Inhaltstext | `holkas` mail connector + `deigma` attachment extraction | Mail-Store getrennt von Content-Extraction halten. |
| `wiki-integration`, `wiki`, `confluence`, `sharepoint` | Wiki/Confluence/SharePoint Discovery/Auth | `holkas` adapters + `adyton` strategies | Cookie/Header/SSLContext als AccessHandle, keine Passwortfelder im Config-Objekt. |
| `ingestion` | ContentDetector, ExtractorRegistry, Document/Block model, Tika extractors | `deigma` | Kern ist bereits adaptiert; schwere Extractors später optional. |
| `indexing` | IndexSource, SourceScanner, IndexRunStatus, delta detection | `tamias`, `acropolis`, `emporion` | Nicht als Pipeline kopieren; in Policy, Harbor und Lifecycle zerlegen. |
| `archive` | CacheRepository, ArchiveRun, ArchiveEntry, hashing, snapshot pipeline | `chalcotheca` | Konzepte adaptieren; H2-Repository später als Infra-Adapter. |
| `rag` | LexicalIndex, SemanticIndex, EmbeddingClient, HybridRetriever, ContextBuilder | `anagraphai`, `pinakes` | Lexical schon weit; Semantic/Hybrid als Pinakes-Ports aufbauen. |
| `jcl`, `service/codeanalytics` | Natural/JCL/COBOL Parser, ExternalCall, CallExtractor | `propylaea` | Erst generisches Source-Modell, dann Parser als Implementierungen. |
| `mcp`, `runtime`, `plugins` | Plugin entry point, ToolSpec, ToolRegistry, MCP tools | `katagogion` | Ports und Registry adaptieren, keine Swing-/MainframeContext-Kopplung. |
| `ui` | Shell, Toolbars, Menüs, Panels | `exedra` | Shell-Framework vorhanden; Business-Panels nicht weiter priorisieren. |

## Empfohlene PR-Reihenfolge

### PR 1 — Architekturleitplanken für `proasteion` (#13)

Ziel: Bevor weitere Adapter kommen, muss der äußere Ring gemeinsame Begriffe und Abhängigkeitsregeln bekommen.

Minimaler Code-Scope:

```text
proasteion/
  OuterAdapter
  AdapterKind
  AdapterCapability
  AdapterRegistration
  AdapterRegistry
```

Akzeptanz:

```text
- Java 8
- keine Swing-Abhängigkeit im proasteion-root
- keine domain policy in proasteion
- README mit erlaubter Abhängigkeitsrichtung
- Test oder Build-Check: astu importiert niemals proasteion oder javax.swing
```

### PR 2 — `holkas` ResourceConnector-Core stabilisieren (#8)

Ziel: Holkas bekommt zuerst ein stabiles SPI, nicht sofort alle Connectoren.

Ergänzen:

```text
ResourceConnectorRegistry
ResourceSchemeHandler
ResourceConnection
ResourceListing
ResourceListingEntry
RawResourceHandle
RawResourceMetadata
ResourceReadMode
```

Aus MainframeMate übernehmen als Tests/Konzepte:

```text
FileNode          -> ResourceListingEntry
FilePayload       -> RawResourceContent + metadata + read mode
PathDialect       -> scheme-specific locator/path dialect
MvsPathDialect    -> MVS-specific dialect in FTP/MVS adapter package
readFileBinary    -> ResourceReadMode.BINARY
readFile          -> ResourceReadMode.TEXT_OR_DEFAULT
```

Noch nicht tun:

```text
- kein echter NDV-Port im selben PR
- keine UI connection tabs
- keine SettingsHelper-Kopplung
- kein CredentialsProvider mit getPassword()
```

### PR 3 — `emporion` als Harbor-Koordination (#15)

Ziel: Die Trennung Scan → Fetch → Inspect aus MainframeMate sauber neu ausdrücken.

Minimaler Code-Scope:

```text
ResourceHarbor
ResourceArrival
ResourceDiscoveryRequest
ResourceDiscoveryResult
ResourceInspectionRequest
ResourceInspectionResult
HarborPipeline
```

Wichtig:

```text
- Emporion koordiniert holkas und deigma.
- Emporion schreibt nicht in Lucene.
- Emporion entscheidet keine Policy.
- Emporion cached nicht dauerhaft.
```

MainframeMate-Vorbild:

```text
SourceScanner.scan()
SourceScanner.scanStreaming()
SourceScanner.fetchContent()
IndexingPipeline.ContentProcessor
ExtractorRegistry
```

Aber die Corenth-Version endet bei einem transportnahen, flach extrahierten Ergebnis.

### PR 4 — `tamias` als Policy-Steward vervollständigen (#5)

Ziel: Die MainframeMate-`IndexSource`-Felder werden nicht als Konfigurations-God-Object kopiert, sondern in Policy-Objekte zerlegt.

Ergänzen:

```text
IndexingPolicy
ResourceScope
PatternRule
PatternRuleType
ChangeDetectionStrategy
ChangeDetectionDecision
CacheInvalidationPolicy
PolicyDecision
IndexingReasonCode
```

MainframeMate-Felder als Quelle:

```text
scopePaths
includePatterns
excludePatterns
maxDepth
maxFileSizeBytes
changeDetection
scheduleMode
securityMode
```

Akzeptanz:

```text
- jede Entscheidung hat maschinenlesbare Reason-Codes
- Policies arbeiten auf VirtualResourceRef + Metadata
- keine SourceType-Enums für FTP/NDV/Mail hart in tamias verdrahten
- Include/Exclude/Size/Depth/Cache-Invalidation sind separat testbar
```

### PR 5 — `acropolis` Lifecycle-Orchestrierung zerlegen (#10)

Ziel: Den vorhandenen `ResourceLifecycleCoordinator` nicht weiter aufblasen, sondern in kleine, testbare Schritte zerlegen.

Ergänzen:

```text
IndexingRun
IndexingRunStatus
IndexingRunSummary
ResourceProcessingPlan
ResourceProcessingStep
ResourceProcessingContext
ResourceProcessingResult
ResourceDeltaDecision
ResourceLifecycleService
```

MainframeMate-Vorbild:

```text
IndexingPipeline:
  scan
  delta detection
  fetch
  extract
  chunk
  index
  delete/tombstone
  status persist
```

Corenth-Ziel:

```text
- scan/discovery kommt aus emporion
- policy kommt aus tamias
- cache/archive kommt aus chalcotheca
- lexical index kommt aus anagraphai
- semantic index kommt optional aus pinakes
- source-code analysis kommt optional aus propylaea
```

### PR 6 — FTP/MVS/JES als erster echter authentifizierter Connector (#8 + adyton)

Ziel: Erst nach stabilen Ports einen realen Mainframe-Transport migrieren.

Adaptieren aus MainframeMate:

```text
CommonsNetFtpFileService
MvsPathDialect
MvsListingService
TrailingPaddingTrimmer
RecordStructureCodec
JesFtpJobSubmitter
JesFtpService
```

Neu schneiden:

```text
FtpResourceConnector
FtpResourceConnection
FtpAuthenticationStrategy
FtpAccessHandle
FtpTransferSettings
FtpReadMode
MvsDatasetLocator
MvsListingParser
JesJobSubmitter
JesSpoolConnector
```

Nicht übernehmen:

```text
SettingsHelper.load()
CredentialsProvider.resolve(...).getPassword()
System.out println logging
UI resource state
```

### PR 7 — `pinakes` Semantic-Ports (#7)

Ziel: Noch keine verpflichtende lokale AI-Runtime, sondern reine Ports und ein kleiner In-Memory-Adapter.

Ergänzen:

```text
SemanticIndex
SemanticDocument
SemanticChunk
EmbeddingClient
EmbeddingModel
EmbeddingVector
EmbeddingRequest
EmbeddingResult
Reranker
RerankRequest
RerankedResult
HybridRetrievalPlan
```

Adaptieren aus MainframeMate:

```text
rag/port/EmbeddingClient
rag/port/SemanticIndex
rag/port/RerankerClient
rag/infrastructure/InMemorySemanticIndex
rag/usecase/HybridRetriever
rag/config/EmbeddingSettings
rag/config/RerankerSettings
```

Nicht übernehmen:

```text
RagService singleton
Chat/context orchestration
cloud clients direkt im pinakes-core
ONNX/WinML/DirectML als Pflichtabhängigkeit
```

### PR 8 — `propylaea` Source-Code-Modell zuerst (#3)

Ziel: Nicht JCL/Natural/COBOL direkt als universelles Modell übernehmen, sondern zuerst ein Corenth-Modell schaffen.

Ergänzen:

```text
SourceLanguage
SourceDocument
ProgramStructure
CodeComponent
CodeComponentKind
SourceLocation
CallRelation
DataAccessRelation
IncludeRelation
SourceParser
SourceParserRegistry
ParsingRequest
ParsingResult
```

Adaptieren aus MainframeMate:

```text
JCLLexer.g4 / JCLParser.g4
AntlrJclParser
NaturalParser
CobolParser
DdmParser
CallExtractor
ExternalCall
LanguageDetector
NaturalCallExtractor
CobolCallExtractor
JclCallExtractor
```

Nicht übernehmen:

```text
JclOutlineModel als generisches Corenth-Modell
JclElementType mit Icons/UI-Displaynamen
RSyntaxTextArea TokenMaker
NaturalSubroutineHighlighter
Mermaid UI-Konvertierung
```

### PR 9 — `katagogion` Plugin-/Tool-/MCP-Ports (#12)

Ziel: Plugins dürfen Corenth erweitern, aber nicht die Schutzgrenzen umgehen.

Ergänzen:

```text
CorenthPlugin
PluginContext
PluginDescriptor
ToolPort
ToolDescriptor
ToolInvocation
ToolResult
ToolRegistry
AdapterProvider
PluginSandboxPolicy
```

Adaptieren aus MainframeMate:

```text
MainframeMatePlugin
McpTool
ToolSpec
ToolRegistry
ToolPolicy
PluginManager ServiceLoader-Idee
MCP registry/client/server-Idee
```

Nicht übernehmen:

```text
MainframeContext mit JFrame/FileTab/OpenTab
globale PluginManager-Singletons
UI menu command coupling
Tools mit direktem Filezugriff ohne tamias/adyton/acropolis
```

## Backend zuerst: konkrete Umsetzung bis zum nächsten stabilen Meilenstein

Ich würde als nächsten Meilenstein definieren:

```text
Ein lokaler oder FTP/MVS-Ressourcenbestand kann über Corenth-Ports entdeckt,
mediated gelesen, policy-geprüft, flach extrahiert, lexikalisch indiziert
und mit nachvollziehbarem Run-/Item-Status abgelegt werden.
```

Minimaler Backend-Slice:

```text
1. proasteion boundary + dependency guard
2. holkas registry + listing/read SPI
3. emporion discovery/fetch/inspect pipeline
4. tamias indexing/cache policy
5. acropolis run/step/status model
6. file: walking skeleton auf neue Pipeline umziehen
7. danach FTP/MVS/JES als erster echter externer Connector
```

## Wichtige technische Tests aus MainframeMate, die migriert werden sollten

| MainframeMate-Test | Corenth-Zieltest |
|---|---|
| `MvsPathDialectTest` | Holkas FTP/MVS path dialect test |
| `MvsLocationTest` | MVS dataset/member locator test |
| `MvsListingServiceTest` | MVS listing parser/strategy test |
| `MvsQuoteNormalizerTest` | MVS quote normalizer test |
| `TrailingPaddingTrimTest` | FTP text transfer cleanup test |
| `RecordStructureCodecTest` | Record-structure codec test, evtl. holkas/deigma boundary test |
| `ConnectionIdTest` | Adyton AccessRequest/CredentialRequest identity test |
| `SessionCipherTest`, `CredentialStoreTest` | Adyton SecretMaterialCache/lease lifecycle tests |
| `DocumentIngestionTest` | Deigma extraction + acropolis integration test |
| `RagSystemTest`, `HybridRetrieverRerankTest` | Pinakes semantic/hybrid retrieval tests |
| `JclDependencyServiceTest`, `NaturalDependencyServiceTest`, `CodeAnalyticsServiceTest` | Propylaea parsing/call relation tests |
| `GrepSearchToolTest`, `ReadFileToolTest`, `StatPathToolTest` | Katagogion tools must go through mediated resource ports |

## Risiken und Gegenmaßnahmen

| Risiko | Gegenmaßnahme |
|---|---|
| Alte MainframeMate-Kopplungen schleichen sich wieder ein | Jeder PR bekommt eine `do-not-copy`-Liste und Dependency-Rule-Test. |
| Authentifizierung wird über Shortcut-APIs umgangen | Keine öffentlichen Passwortgetter; echte Connectoren nur über `AccessBroker`/`AccessHandle`. |
| Holkas wird zu einem neuen FileService-Monolithen | Connector/Connection/Listing/Content/Metadata/ReadMode sauber trennen. |
| Acropolis wird zu einem neuen `IndexingService`-Singleton | Run/Plan/Step/Result modellieren und Services injizieren. |
| Tamias wird zu einer SourceType-Konfigurationsklasse | Policies auf ResourceScheme/VirtualResourceRef/Metadata anwenden, nicht auf feste FTP/NDV/Mail-Enums. |
| Pinakes zieht Runtime-Komplexität in den Core | Nur Ports im Core; ONNX/WinML/HTTP als optionale Adapter. |
| Propylaea übernimmt UI-Outline-Modelle | Generisches Source-Modell zuerst; alte Parser nur als Adapter. |
| Katagogion erlaubt Tools direkten Zugriff auf Files/Netzwerk | Tools erhalten nur Application-/Mediated-Ports und Policy-Kontext. |

## Entscheidung für die offenen Issues

| Issue | Empfehlung |
|---:|---|
| #30 | Als konkrete Swing-Shell akzeptieren, nach kurzer Prüfung schließen/einfrieren. Keine weitere Backend-Energie dort binden. |
| #27 | Nach CI/Testlauf wahrscheinlich schließen. Chunking ist vorhanden. |
| #20 | Als Analyse-Ticket schließen, da Analyse-Dokument und Adyton-Modell vorhanden sind. |
| #13 | Als nächstes umsetzen. Gemeinsame Adaptergrenze und Dependency-Regeln. |
| #8 | Danach Holkas-SPI stabilisieren, echte Connectoren erst schrittweise. |
| #15 | Direkt nach Holkas-Core: Emporion als Harbor-Pipeline. |
| #5 | Tamias um Policy-/Cache-/ChangeDetection-Modell ergänzen. |
| #10 | Acropolis um Run-/Plan-/Step-/Status-Modell erweitern. |
| #7 | Pinakes ports-first, keine Runtime-Integration im ersten PR. |
| #3 | Propylaea model-first, Parser danach adaptieren. |
| #12 | Katagogion ports-first, MCP/ServiceLoader später konkretisieren. |
| #11 | Als Thin-Adapter-Regel neu interpretieren: Exedra darf Swing sein, aber keine Fachlogik besitzen. |

## Konkreter nächster Schritt

Der nächste PR sollte nicht aus MainframeMate kopieren, sondern die Grundlage für alle Backend-Adapter legen:

```text
PR: proasteion boundary + holkas connector registry skeleton
```

Inhalt:

```text
proasteion:
  OuterAdapter
  AdapterKind
  AdapterCapability
  AdapterRegistration
  AdapterRegistry

holkas:
  ResourceConnectorRegistry
  ResourceSchemeHandler
  ResourceConnection
  ResourceListing
  ResourceListingEntry
  RawResourceMetadata
  ResourceReadMode

Tests:
  registry rejects duplicate scheme handlers
  file: connector still fetches bytes
  no astu -> proasteion imports
  no astu/acropolis/chalcotheca -> javax.swing imports
```

Danach kann MainframeMate-FTP/MVS/JES fachlich sicher in einen echten Adapter überführt werden, ohne die alten Auth- und UI-Kopplungen zurückzubringen.
