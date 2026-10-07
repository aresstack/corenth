# ADR-0001: Ort des produktiven Kompositionspunkts

**Status:** angenommen (Session 0, 2026-10-06), umgesetzt in #10 Slice 2 (siehe „Umsetzung“) · **Bezug:** Issue #10 (Pflicht-Entscheidung vor Slice 2), `proasteion/README.md` (#13-YAGNI-Entscheidung), `docs/architecture-notes.md`

## Kontext

Corenth besitzt beide Seiten des vermittelten Ressourcen-Lifecycles: den Chalcotheca-Schalter (`MediatedResourceService`, Vertrag `MediatedResourceAccess`) und den Acropolis-Lifecycle (`ResourceLifecycleCoordinator`), der seit #10 Slice 1 ausschließlich über diesen Vertrag liest. Beide werden bislang nur in Tests zusammengesetzt. Es gibt keinen produktiven Code, der Tamias-Policy, Archiv, Holkas-`AcquisitionPort`, Deigma-Inspektion, Lucene-Index und Lifecycle verdrahtet. Issue #10 verlangt, dass der Ort dieser Komposition entschieden und dokumentiert wird, bevor Slice 2 Bootstrap-Code hinzufügt.

Randbedingungen aus der bestehenden Architektur:

- `astu`/`adyton` dürfen nicht von `proasteion` abhängen (ArchUnit `INNER_CITY_MUST_NOT_DEPEND_ON_OUTER_RING`). Acropolis darf also weder Holkas, noch Platform-Adapter, noch UI konstruieren.
- Der `proasteion`-Root bleibt nach der #13-Entscheidung ein Boundary-/Dokumentationsmodul ohne Klassen und wird kein Service Locator.
- Exedra ist ein austauschbarer Swing-Shell-Adapter (`EXEDRA_MUST_STAY_THIN_UI_SHELL`) und darf keine Voraussetzung des Backends werden. CI, Tests, spätere CLI- oder Server-Hosts brauchen die Komposition ohne Display.
- Keine globalen Singletons, keine statischen Zugriffe auf Abhängigkeiten; alle konkreten Adapter werden außen verdrahtet.
- Die ArchUnit-Regeln und die Projektliste in `architecture-tests/build.gradle` müssen das neue Modul erfassen.

## Betrachtete Alternativen

### A. Dediziertes äußeres Application-/Bootstrap-Modul `proasteion:application` (gewählt)

Ein kleines Gradle-Submodul im Außenring, das ausschließlich komponiert: es kennt alle Adapter (`emporion:holkas`, `emporion:deigma`, `platform:*`) und die inneren Verträge (`astu`, `acropolis`, `chalcotheca`, `tamias`, `anagraphai`, `adyton`) und liefert fertig verdrahtete Use-Case-Einstiege (z. B. Lifecycle und Suche) an Hosts aus.

- Pro: neutral gegenüber Hosts; headless nutzbar; einziger Ort, an dem Connectoren, Secret-Provider und Policies zusammengesteckt werden; Dependency-Richtung bleibt strikt nach innen.
- Pro: `katagogion`-Tools, CLI/Server und Exedra konsumieren denselben Kompositionspunkt statt eigene Verdrahtung zu wiederholen.
- Contra: ein weiteres Modul; die ArchUnit-Projektliste und die UI-Freiheitsregel müssen erweitert werden (der Guard in `architecture-tests/build.gradle` erzwingt die Listung).

### B. Komposition in einem konkreten Client, z. B. Exedra (verworfen)

- Contra: koppelt die einzige produktive Komposition an Swing und ein Display; CI-/Headless-Betrieb und spätere Hosts müssten die Verdrahtung duplizieren.
- Contra: Exedra müsste Holkas, Tamias und Adyton kennen, was die Regeln `CLIENT_ADAPTERS_MUST_NOT_BYPASS_*` und `EXEDRA_MUST_STAY_THIN_UI_SHELL` gezielt aushöhlt.

### C. Komposition im `proasteion`-Root (verworfen)

- Contra: widerspricht der #13-YAGNI-Entscheidung, den Root klassenfrei zu halten; ein Root-Bootstrap würde schleichend zum impliziten Service Locator für alle Adapterfamilien.

### D. Komposition innerhalb von `acropolis` (verworfen)

- Contra: verletzt die Inner-City-Regel, weil Acropolis dann Holkas-, Deigma- und Platform-Klassen konstruieren müsste. Slice 1 hat den letzten acropolis-eigenen Beschaffungs-Port (`RawResourceProvider`) entfernt, über den Tests die Tamias-Vermittlung umgehen konnten; eine Komposition in `acropolis` würde einen solchen Umweg wieder einführen und zusätzlich die Inner-City-Regel verletzen.

### E. Dauerhaft nur Testkomposition (verworfen)

- Contra: lässt die vom Inventar benannte „Zentrale Lücke“ bestehen; grüne Tests gegen Fakes beweisen keine produktive Integration.

## Entscheidung

Der produktive Kompositionspunkt entsteht in #10 Slice 2 als neues Gradle-Submodul **`proasteion:application`** (Paket `com.aresstack.corenth.proasteion.application`). Es ist ein *Adapter nach innen*: es implementiert keine Fachlogik, sondern verdrahtet Ports mit konkreten Adaptern und gibt Use-Case-Einstiege heraus.

Leitplanken für Slice 2:

1. **Dependency-Richtung:** `application` → `proasteion:*`-Adapter und → `astu`/`adyton`-Verträge. Nichts aus `astu`, `adyton` oder den Adapterfamilien hängt von `application` ab. Hosts (Exedra, spätere CLI/Server, `katagogion`-Tools) hängen von `application` ab, nie umgekehrt.
2. **Öffentliche API des Moduls** exponiert nur innere Verträge (z. B. `ResourceLifecycleCoordinator`, `SearchCoordinator`, `MediatedResourceAccess`), niemals Holkas-, Tamias-, Deigma- oder Adyton-Implementierungstypen. Damit bleibt Exedra als Konsument regelkonform.
3. **Kein Display, keine UI-Technologie** im Modul; die Komposition muss in CI und in Tests ohne Swing ausführbar sein.
4. **Keine globalen Singletons.** Der Einstieg ist eine explizit instanziierte Fabrik oder Builder; Abhängigkeiten werden übergeben, nicht statisch aufgelöst.
5. **Policy ist eine Kompositionsentscheidung.** Es existiert heute keine produktive `ResourceAccessPolicy`-Implementierung. Slice 2 muss die erste explizit wählen und dokumentieren (eine „erlaube alles“-Policy ist nur als bewusst benannter Platzhalter zulässig, bis #5 die komponierten Policies liefert).
6. **Secret-Quellen** werden hier verkettet (KeePassRPC, später #43-Provider). Der KeePassRPC-Adapter ist bis zur Korrektur seiner Lookup-Bindung an die reale `keepassrpc-java`-API kein bewiesener produktiver Pfad (siehe `docs/migration/migration-inventory.md` §2.1).

## Konsequenzen für ArchUnit und Build

- `architecture-tests/build.gradle`: `:proasteion:application` muss in `architectureProjects` aufgenommen werden; der Konfigurations-Guard schlägt sonst fehl.
- `CorenthArchitectureRulesTest`: neue Regel „`application` darf nicht von `javax.swing..`, `java.awt..`, `javafx..` und `exedra..` abhängen“; neue Regel „keine Klasse außerhalb von `application` und den Hosts hängt von `application` ab“ (konkret: `astu..`, `adyton..`, `emporion..`, `platform..` dürfen `application` nicht referenzieren).
- Bestehende Regeln gelten unverändert: Die Inner-City-Regel verhindert bereits, dass `astu`/`adyton` das Modul kennen; `ACROPOLIS_LIFECYCLE_MUST_ACQUIRE_THROUGH_MEDIATED_ACCESS` stellt sicher, dass die konkrete Verdrahtung des Schalters nur außen stattfindet.
- Die Zyklenregel `TOP_LEVEL_CITY_DISTRICTS_MUST_BE_FREE_OF_CYCLES` bleibt erfüllt, weil `application` und `exedra` im selben Top-Level-District `proasteion` liegen; ein Sub-District-Zyklus zwischen beiden ist durch Leitplanke 1 ausgeschlossen.

## Ausdrücklich nicht Teil dieser Entscheidung

Diese ADR legt den Ort fest. Sie erzeugt kein Modul und keinen Bootstrap-Code; beides ist #10 Slice 2. Die Adyton-Station (Slice 3), das Run-/Plan/Step-Modell (Slice 4) und die Konsolidierung der Chalcotheca-Caches gegen #33/#5 (Slice 5) bleiben davon unberührt.

## Umsetzung (#10 Slice 2)

Die Entscheidung bleibt unverändert; dieser Abschnitt dokumentiert nur, wie sie umgesetzt wurde.

- **Modul:** `proasteion:application`, Paket `com.aresstack.corenth.proasteion.application`, in `settings.gradle` und in `architectureProjects` eingetragen. Abhängigkeiten: `api` auf `astu:acropolis` (innere Verträge), `implementation` auf `emporion:holkas` und `emporion:deigma`.
- **Öffentliche API (Leitplanken 2 und 4):** `CorenthComposition` (explizit instanziiert, zustandslos) mit `composeLocal(ApplicationSettings)`; Ergebnis ist der unveränderliche, schließbare Kontext `CorenthApplication` mit `resourceLifecycle()`, `search()` und `mediatedResourceAccess()`. Kein Holkas-, Deigma-, Tamias-Implementierungs- oder Adyton-Typ ist über diese API erreichbar.
- **Verdrahtung:** `LocalFileRootsAccessPolicy` → `MediatedResourceService` (als `MediatedResourceAccess`) → `HolkasAcquisitionPort` mit `FileSystemResourceConnector`; `DeigmaContentInspector` (paketprivat, `SimpleContentDetector` + PlainText-/Markdown-Extraktor) als `ContentInspector`; `PatternResourcePolicy` aus den Host-Einstellungen; `LuceneLexicalIndex` + `NlpTextChunker`; `ResourceLifecycleCoordinator` und `SearchCoordinator`. Ein `InMemoryResourceArchive` wird als heutige `ResourceArchive`-Kompatibilitätsfassade von Schalter und Lifecycle gemeinsam genutzt.
- **Policy (Leitplanke 5):** Die erste produktive `ResourceAccessPolicy` ist `tamias.LocalFileRootsAccessPolicy`: deny by default, nur `file:`, nur unterhalb explizit konfigurierter Wurzeln, nur der Lesepfad (`LIST_CHILDREN`, `READ_METADATA`, `READ_CONTENT`, `FETCH_EXTERNAL`). Sie ist bewusst keine „erlaube alles“-Policy; akteurspezifische Regeln und Change-Detection-Policies liefert #5.
- **Deigma-Adapter:** liegt im Kompositionsmodul, nicht in Deigma, weil `ContentInspector` ein Lifecycle-Port ist und Deigma laut `DEIGMA_MUST_STAY_SHALLOW_EXTRACTION` ohne Lifecycle-Kopplung bleibt.
- **ArchUnit:** neue Regeln `APPLICATION_MUST_STAY_HEADLESS`, `ONLY_HOSTS_MAY_DEPEND_ON_APPLICATION` (für `adyton`, `astu`, `emporion`, `platform`, `katagogion`), `ACQUISITION_BRIDGE_IS_WIRED_ONLY_AT_COMPOSITION_ROOT`, `APPLICATION_MUST_NOT_DECIDE_POLICIES`, `APPLICATION_MUST_NOT_HOLD_STATIC_STATE`. Bestehende Regeln, inklusive Secret-Containment, sind unverändert.
- **Leitplanke 6 (Secret-Quellen)** ist noch nicht umgesetzt: `file:` braucht keine Credentials; die Adyton-Station folgt in Slice 3.
- *Nachtrag Nachtintegration 2026-10-07 (lokal):* Die Station existiert (`AcquisitionAccessPort` in Chalcotheca, `BrokeredAcquisitionAccess` in Holkas, #10 Slice 3); `composeLocal` setzt ausdrücklich `AcquisitionAccessPort.unauthenticated()`, weil der lokale Pfad keine authentifizierte Quelle hat. Die KeePassRPC-Lookup-Bindung ist an die reale `keepassrpc-java`-API korrigiert und der interaktive Prompt-Provider existiert (#43); eine Quellenkette ist noch nicht verdrahtet. Regel für sie: nur bei einfacher `SecretUnavailableException` zur nächsten Quelle weiterfallen, bei `AuthCancelledException` und `SecretReleaseDeniedException` abbrechen.
