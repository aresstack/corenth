# Application

Outer composition root of Corenth ([ADR-0001](../../docs/adr/0001-composition-root.md), #10 Slice 2).

`proasteion:application` is the only productive place where Tamias policies, the Chalcotheca archive counter, Holkas acquisition, Deigma inspection, the Anagraphai index and the Acropolis use cases are put together. It contains no business logic: it instantiates and connects, and hands the result to hosts.

## Usage

```java
ApplicationSettings settings = ApplicationSettings.builder()
        .indexDirectory(indexDir)
        .accessibleRoot(documentsDir)       // at least one; reads outside are denied by Tamias
        .indexedPattern("**/*.md")          // optional; empty = every file below the roots
        .excludedPattern("**/.git/**")      // optional
        .maxIndexedBytes(10L * 1024 * 1024) // optional; 0 = unlimited
        .build();

try (CorenthApplication app = new CorenthComposition().composeLocal(settings)) {
    app.resourceLifecycle().process(ref);   // ResourceLifecycleCoordinator
    app.search().search("bronze", 10);      // SearchCoordinator
    app.mediatedResourceAccess();           // MediatedResourceAccess for browsing hosts
}
```

## What is wired

```text
LocalFileRootsAccessPolicy (Tamias, read-only, deny by default)
  -> MediatedResourceService (Chalcotheca counter, exposed as MediatedResourceAccess)
     -> HolkasAcquisitionPort -> FileSystemResourceConnector (file:)
  -> DeigmaContentInspector (SimpleContentDetector, PlainText/Markdown extractors) as ContentInspector
  -> PatternResourcePolicy (Tamias indexing rule from the host settings)
  -> LuceneLexicalIndex + NlpTextChunker (Anagraphai)
  -> ResourceLifecycleCoordinator, SearchCoordinator (Acropolis)
```

`InMemoryResourceArchive` is the current `ResourceArchive` compatibility facade; one instance is shared by the counter and the lifecycle until #33 replaces it with the record/history repository.

## Rules

- Public API (`CorenthComposition`, `CorenthApplication`, `ApplicationSettings`) exposes inner contracts only; Holkas, Deigma and Tamias implementation types stay internal.
- Headless: no Swing, AWT, JavaFX or Exedra. Tests run with `java.awt.headless=true`.
- Explicit instantiation, no static state, no service locator; each `composeLocal` call builds an independent object graph that the caller closes.
- Only hosts (Exedra, a future CLI/server, `katagogion` tools) may depend on this module; the inner city, Emporion and Platform never do.
- Policies are Tamias classes. The composition selects and parameterises them but never implements `ResourceAccessPolicy` or `ResourcePolicy`.

These rules are enforced in `architecture-tests` (`APPLICATION_*`, `ONLY_HOSTS_MAY_DEPEND_ON_APPLICATION`, `ACQUISITION_BRIDGE_IS_WIRED_ONLY_AT_COMPOSITION_ROOT`).

## Not yet wired

Adyton access preparation (#10 Slice 3), the run/plan/step model (Slice 4), #33/#5 integration (Slice 5), further connectors (FTP, HTTP, mail), heavy Deigma extractors (#42) and secret providers (#43).
