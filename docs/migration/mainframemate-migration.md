**MainframeMate ist die Erfahrungsquelle, Corenth bleibt die saubere Reimplementierung.** Wir sollten nicht in den Modus kommen, alten Code paketweise umzuhängen. Gerade bei Authentifizierung, externen Ressourcen und Index-Lifecycle ist das neue Konzept deutlich sauberer als MainframeMate.

Ich habe den hochgeladenen MainframeMate-Stand gegen den aktuellen Corenth-Stand geprüft. Wichtig: Die Java-Quellen aus dem Upload sind bereits hashgleich im aktuellen Corenth-Zip unter `research/` enthalten. Das heißt, wir können `research/` als Referenzbestand nutzen, aber die eigentliche Implementierung muss weiter in den neuen Corenth-Modulen entstehen.

Download der ausführlichen Backend-Reimplementierungsplanung:
[corenth-mainframemate-backend-reimplementation-plan-2026-06-02.md](sandbox:/mnt/data/corenth-mainframemate-backend-reimplementation-plan-2026-06-02.md)

## Meine aktualisierte Einschätzung

`#30` sollten wir als **UI-Fundament erledigt/einfrierbar** behandeln. Exedra darf eine konkrete Swing-UI sein. Das ist kein Architekturproblem, solange die Richtung stimmt:

```text
exedra  ->  Use Cases / Ports  ->  acropolis / chalcotheca / tamias / anagraphai / ...
```

Nicht erlaubt wäre:

```text
astu / acropolis / chalcotheca  ->  exedra
```

Die Backend-Arbeit sollte jetzt nicht mehr in UI-Komponenten fließen. Das vorbereitete IntelliJ-artige Main Window, Menüs, Shortcuts, Toolbar und Toolwindows reichen als Rahmen. Die Fachfunktionen werden später nur darüber gelegt.

## Wichtigste Leitplanke

Die alte MainframeMate-`VirtualResource` war eine gute Idee, aber noch nicht konsequent genug getrennt. Corenth macht es besser:

```text
BookmarkUri / VirtualResourceRef
    stabile Resource-Identität

holkas
    rohe externe Ressource beschaffen

deigma
    flache Content-Erkennung und Extraktion

tamias
    Policy, Zugriff, Cache-/Index-Entscheidung

chalcotheca
    Archive, Cache, Lifecycle-State

acropolis
    Orchestrierung

anagraphai / pinakes / propylaea
    abgeleitete Register und tiefe Analyse
```

Deshalb sollten UI, Tools und Plugins **nicht direkt Holkas-Connectoren verwenden**. Der saubere Pfad ist:

```text
exedra / katagogion / application use case
  -> acropolis / chalcotheca mediated access
  -> tamias policy decision
  -> adyton access if credentials are needed
  -> holkas acquisition internally
  -> deigma extraction
  -> chalcotheca snapshot/cache
  -> anagraphai / pinakes / propylaea
```

Das schützt genau die neue Architektur, die du bewusst zugeschnitten hast.

## Was ich jetzt als Nächstes machen würde

Ich würde **nicht** mit Pinakes, Propylaea oder allen Connectoren gleichzeitig starten. Der nächste stabile Backend-Slice sollte sein:

```text
1. #13 proasteion boundary
2. #8 holkas connector SPI
3. #15 emporion harbor pipeline
4. #5 tamias policy completion
5. #10 acropolis lifecycle/run model
6. danach FTP/MVS/JES als erster echter externer Connector
```

## Konkrete Umsetzungsidee

### 1. `proasteion` zuerst stabilisieren

Das Root-Modul ist aktuell noch leer. Dort sollten minimale gemeinsame Adapterbegriffe entstehen:

```text
OuterAdapter
AdapterKind
AdapterCapability
AdapterRegistration
AdapterRegistry
```

Wichtig: keine UI, keine Connector-Details, keine Policy. Nur die gemeinsame Sprache für äußere Adapter.

### 2. `holkas` nicht als neuen `FileService`-Monolithen bauen

MainframeMate hatte mit `FileService`, `FileNode`, `FilePayload`, `PathDialect`, `MvsPathDialect`, FTP/JES/NDV usw. viel nützliche Erfahrung. In Corenth würde ich das aber zerlegen:

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

Dann können `file:`, `ftp:`, `ndv:`, `mail:`, `wiki:`, `confluence:` usw. später einzeln kommen, ohne dass Holkas wieder zu einem Alleskönner wird.

### 3. `emporion` als Harbor-Pipeline

Die MainframeMate-Trennung `scan -> fetch -> process` ist gut, aber die alte `IndexingPipeline` darf nicht komplett nach `emporion`. In Corenth sollte `emporion` nur den Hafenfluss koordinieren:

```text
holkas obtains raw resource
deigma detects and extracts shallow content
emporion returns a harbor-level result
```

Kein Lucene, keine Embeddings, keine Policy, kein dauerhafter Cache.

### 4. `tamias` ausbauen

Aus MainframeMate sind vor allem diese Policy-Konzepte wertvoll:

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

Aber nicht als großes `IndexSource`-Objekt kopieren. Besser:

```text
IndexingPolicy
ResourceScope
PatternRule
ChangeDetectionStrategy
CacheInvalidationPolicy
PolicyDecision
IndexingReasonCode
```

Jede Entscheidung sollte einen maschinenlesbaren Grund haben.

### 5. `acropolis` zerlegen

Der vorhandene `ResourceLifecycleCoordinator` ist als Walking Skeleton gut. Aber er sollte nicht weiter anwachsen. Aus MainframeMate können wir das Run-/Status-Denken übernehmen:

```text
IndexingRun
IndexingRunStatus
IndexingRunSummary
ResourceProcessingPlan
ResourceProcessingStep
ResourceProcessingContext
ResourceProcessingResult
ResourceDeltaDecision
```

Damit wird der Backend-Lifecycle nachvollziehbar und testbar.

## Wichtigster Punkt bei Authentifizierung

Hier dürfen wir auf keinen Fall zurückfallen.

MainframeMate hatte viele Stellen mit raw credentials:

```text
Credentials.getPassword()
CredentialStore.resolve()
String[]{user, password}
FTPClient.login(user, password)
NdvService.connect(host, port, user, password)
Authorization header construction
```

Corenth sollte das konsequent vermeiden. Echte Connectoren bekommen später nur:

```text
AccessBroker
AuthenticationStrategy
AccessHandle
AccessGrant
CredentialLease
```

Raw secret material darf nur innerhalb einer konkreten `AuthenticationStrategy` existieren. FTP, NDV, Wiki, Confluence Basic Auth und mTLS werden dann jeweils eigene Strategien.

## Meine konkrete Ticket-Entscheidung

| Issue | Empfehlung                                                          |
| ----: | ------------------------------------------------------------------- |
| `#30` | Als konkrete Swing-Shell akzeptieren, prüfen, schließen/einfrieren. |
| `#27` | Nach CI/Testlauf wahrscheinlich schließen.                          |
| `#20` | Als Analyse-Ticket schließen, Adyton-Modell ist vorhanden.          |
| `#13` | Jetzt als nächstes umsetzen.                                        |
|  `#8` | Danach Holkas-SPI, noch nicht alle Connectoren.                     |
| `#15` | Danach Emporion als Harbor-Pipeline.                                |
|  `#5` | Danach Tamias Policy/Cache/ChangeDetection.                         |
| `#10` | Danach Acropolis Run-/Step-/Status-Modell.                          |
|  `#7` | Ports-first, keine AI-Runtime im ersten PR.                         |
|  `#3` | Model-first, Parser danach.                                         |
| `#12` | Plugin-/Tool-Ports, noch keine große Runtime.                       |
| `#11` | Als Thin-Adapter-Regel interpretieren, nicht als UI-Neutralität.    |

## Nächster sinnvoller PR

Ich würde den nächsten PR so schneiden:

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
```

Tests:

```text
registry rejects duplicate scheme handlers
file: connector still fetches bytes
astu imports no proasteion package
core modules import no javax.swing
```

Das wäre aus meiner Sicht der richtige nächste Schritt: **Backend-Grenzen härten, dann alte MainframeMate-Erkenntnisse kontrolliert Adapter für Adapter neu implementieren.**
