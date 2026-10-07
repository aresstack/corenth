package com.aresstack.corenth.proasteion.application;

import com.aresstack.corenth.astu.acropolis.ContentInspector;
import com.aresstack.corenth.astu.acropolis.ResourceLifecycleCoordinator;
import com.aresstack.corenth.astu.acropolis.SearchCoordinator;
import com.aresstack.corenth.astu.acropolis.chalcotheca.AcquisitionAccessPort;
import com.aresstack.corenth.astu.acropolis.chalcotheca.AcquisitionPort;
import com.aresstack.corenth.astu.acropolis.chalcotheca.InMemoryResourceArchive;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceAccess;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceService;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LexicalIndexConfig;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.LuceneLexicalIndex;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking.BreakIteratorSentenceSegmenter;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking.LexicalChunker;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking.LexicalChunkingConfig;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking.LuceneTokenCounter;
import com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai.chunking.NlpTextChunker;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorIdentity;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorType;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.IndexingRule;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.LocalFileRootsAccessPolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.PatternResourcePolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessPolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourcePolicy;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.change.DigestChangeDetection;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.disposition.DerivativeDispositionPolicy;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRegistry;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.MarkdownTextExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.PlainTextExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.html.HtmlDocumentExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.impl.SimpleContentDetector;
import com.aresstack.corenth.proasteion.emporion.deigma.office.DocxDocumentExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.office.XlsxDocumentExtractor;
import com.aresstack.corenth.proasteion.emporion.deigma.pdf.PdfDocumentExtractor;
import com.aresstack.corenth.proasteion.emporion.holkas.DefaultResourceConnectorRegistry;
import com.aresstack.corenth.proasteion.emporion.holkas.FileSystemResourceConnector;
import com.aresstack.corenth.proasteion.emporion.holkas.HolkasAcquisitionPort;

import java.io.Closeable;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The productive outer composition point of Corenth (ADR-0001, #10 Slice 2).
 *
 * <p>It wires the stable contracts on main into a headless application context:
 * <pre>
 * Tamias LocalFileRootsAccessPolicy
 *   → Chalcotheca MediatedResourceService (archive counter, implements MediatedResourceAccess)
 *   → AcquisitionPort ← HolkasAcquisitionPort ← FileSystemResourceConnector
 *   → ContentInspector ← Deigma (SimpleContentDetector; text, Markdown, HTML, PDF, DOCX, XLSX)
 *   → Anagraphai LuceneLexicalIndex + NlpTextChunker
 *   → Acropolis ResourceLifecycleCoordinator (Tamias change detection and disposition on the
 *     #33 records) and SearchCoordinator
 * </pre>
 *
 * <p>The class only instantiates and connects; it holds no state, makes no access or indexing
 * decision of its own and is not a service locator. Every call to
 * {@link #composeLocal(ApplicationSettings)} builds an independent object graph. Policies come
 * from Tamias; the host settings only parameterise them.
 *
 * <p>One in-memory archive is shared by the counter and the lifecycle: the counter tombstones
 * through its {@code ResourceArchive} facade, the lifecycle reads and writes the authoritative
 * resource records behind it (#33), so there is a single truth about versions, the indexed
 * version and removals at the source. Tamias decides about changes and derivative state (#5).
 */
public final class CorenthComposition {

    /** Name of the single Tamias indexing rule derived from the host settings. */
    static final String INDEXING_RULE_NAME = "host-configured-local-files";

    /**
     * Composes the local {@code file:} backend.
     *
     * @param settings host settings; must not be {@code null}
     * @return a new, independent application context; the caller owns and must close it
     * @throws IOException if the lexical index cannot be opened
     */
    public CorenthApplication composeLocal(ApplicationSettings settings) throws IOException {
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        InMemoryResourceArchive archive = new InMemoryResourceArchive();
        MediatedResourceAccess counter = archiveCounter(settings, archive);
        ContentInspector inspector = deigmaInspector();
        ResourcePolicy indexingPolicy = indexingPolicy(settings);

        final LuceneTokenCounter tokenCounter = new LuceneTokenCounter();
        LexicalChunker chunker = new NlpTextChunker(
                new BreakIteratorSentenceSegmenter(), tokenCounter, new LexicalChunkingConfig());
        Closeable tokenCounterResource = new Closeable() {
            @Override
            public void close() {
                tokenCounter.close();
            }
        };

        LexicalIndex lexicalIndex;
        try {
            lexicalIndex = new LuceneLexicalIndex(new LexicalIndexConfig(settings.indexDirectory()));
        } catch (IOException e) {
            tokenCounter.close();
            throw e;
        } catch (RuntimeException e) {
            tokenCounter.close();
            throw e;
        }

        ResourceLifecycleCoordinator lifecycle = new ResourceLifecycleCoordinator(
                counter,
                new ActorIdentity(settings.lifecycleActorId(), ActorType.SERVICE),
                inspector,
                indexingPolicy,
                archive.records(),
                lexicalIndex,
                chunker,
                new DigestChangeDetection(),
                new DerivativeDispositionPolicy(),
                Clock.systemUTC());

        List<Closeable> ownedResources = new ArrayList<Closeable>();
        ownedResources.add(tokenCounterResource);
        ownedResources.add(lexicalIndex);
        return new CorenthApplication(lifecycle, new SearchCoordinator(lexicalIndex), counter, ownedResources);
    }

    /** Chalcotheca counter: Tamias decides, Holkas acquires behind the internal port. */
    private static MediatedResourceAccess archiveCounter(ApplicationSettings settings, InMemoryResourceArchive archive) {
        ResourceAccessPolicy accessPolicy = new LocalFileRootsAccessPolicy(settings.accessibleRoots(), settings.sourceRefresh());
        AcquisitionPort acquisition = new HolkasAcquisitionPort(
                DefaultResourceConnectorRegistry.of(new FileSystemResourceConnector()));
        // The local composition has no authenticated source: the access station never needs the vault.
        // Authenticated sources plug in a Holkas BrokeredAcquisitionAccess here (#10 Slice 3).
        return new MediatedResourceService(accessPolicy, acquisition, archive, AcquisitionAccessPort.unauthenticated());
    }

    /** Deigma detection and the registered extractors (#42), behind the lifecycle port. */
    private static ContentInspector deigmaInspector() {
        ExtractionRegistry extractors = new ExtractionRegistry();
        extractors.register(new PlainTextExtractor());
        extractors.register(new MarkdownTextExtractor());
        extractors.register(new HtmlDocumentExtractor());
        extractors.register(new PdfDocumentExtractor());
        extractors.register(new DocxDocumentExtractor());
        extractors.register(new XlsxDocumentExtractor());
        return new DeigmaContentInspector(new SimpleContentDetector(), extractors);
    }

    /** Tamias indexing policy (deny by default) parameterised with the host's file patterns. */
    private static ResourcePolicy indexingPolicy(ApplicationSettings settings) {
        IndexingRule rule = new IndexingRule(
                INDEXING_RULE_NAME,
                Collections.singletonList("file"),
                settings.indexedPatterns(),
                settings.excludedPatterns(),
                settings.maxIndexedBytes());
        return new PatternResourcePolicy(Collections.singletonList(rule));
    }
}
