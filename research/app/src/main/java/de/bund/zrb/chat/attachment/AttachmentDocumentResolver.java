package de.bund.zrb.chat.attachment;

import de.bund.zrb.ingestion.model.DocumentSource;
import de.bund.zrb.ingestion.model.ExtractionResult;
import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import de.bund.zrb.ingestion.usecase.BuildDocumentFromTextUseCase;
import de.bund.zrb.ingestion.usecase.ExtractTextFromDocumentUseCase;
import de.zrb.bund.newApi.ui.AppTab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generic resolver that produces a {@link Document} for an attachment from a tab.
 * <p>
 * Resolution strategy (no file-type-specific knowledge in this class):
 * <ol>
 *     <li>If the tab is a {@link DocumentSourceTab} and already exposes a non-empty
 *         pre-built {@link Document}, that document is reused as-is. This is the normal
 *         path: the preview tab was opened via the ingestion pipeline and the document
 *         is already chunkable text.</li>
 *     <li>Otherwise, if the source exposes raw bytes via
 *         {@link DocumentSourceTab#getRawBytes()}, those bytes are sent through the
 *         generic ingestion pipeline ({@link ExtractTextFromDocumentUseCase#execute}, which
 *         is timeout-aware) so binary formats (PDF, DOCX, ...) are handled by their
 *         respective extractors instead of being inlined verbatim.</li>
 *     <li>Otherwise, the textual content from {@link AppTab#getContent()} is used to build
 *         a document — but only if it does not start with a known unsafe binary signature
 *         (e.g. {@code %PDF}, ZIP magic, embedded NUL bytes). This safety net keeps raw
 *         binary signatures out of the hidden chat context.</li>
 * </ol>
 * The resolver is intentionally agnostic of any particular file format. PDF detection and
 * PDF text extraction live entirely inside the ingestion layer.
 * <p>
 * Every resolution returns a {@link ResolveResult} carrying a {@link Status} and warnings
 * even when no document was produced — the caller can therefore surface meaningful error
 * messages instead of a generic "no content" failure.
 */
public class AttachmentDocumentResolver {

    /**
     * Factory for {@link ExtractTextFromDocumentUseCase} instances.
     * <p>
     * Constructing the extractor is expensive (initialises the Tika detector, extractor
     * registry, etc.). This factory enables lazy initialization: the extractor is only
     * instantiated the first time {@link #ingestFromBytes} is actually called, not when a
     * plain-text tab is attached or when the resolver itself is constructed. Injecting a
     * pre-built factory in tests also allows the expensive Tika stack to be replaced with
     * a lightweight stub.
     */
    interface ExtractUseCaseFactory {
        ExtractTextFromDocumentUseCase create();
    }

    private static final Logger LOG = Logger.getLogger(AttachmentDocumentResolver.class.getName());

    /** Lazily initialized on first raw-byte ingestion via double-checked locking in {@link #extractUseCase()}. */
    private volatile ExtractTextFromDocumentUseCase extractUseCase;
    private final ExtractUseCaseFactory extractUseCaseFactory;
    private final BuildDocumentFromTextUseCase buildUseCase;

    public AttachmentDocumentResolver() {
        this(new BuildDocumentFromTextUseCase());
    }

    public AttachmentDocumentResolver(BuildDocumentFromTextUseCase buildUseCase) {
        this.buildUseCase = buildUseCase;
        this.extractUseCaseFactory = new ExtractUseCaseFactory() {
            @Override
            public ExtractTextFromDocumentUseCase create() {
                return new ExtractTextFromDocumentUseCase();
            }
        };
        this.extractUseCase = null; // created lazily
    }

    /**
     * Constructor for tests: accepts a custom {@link ExtractUseCaseFactory} so the
     * expensive Tika/extractor stack can be replaced with a lightweight stub and
     * factory invocations can be tracked.
     */
    AttachmentDocumentResolver(ExtractUseCaseFactory factory, BuildDocumentFromTextUseCase buildUseCase) {
        this.buildUseCase = buildUseCase;
        this.extractUseCaseFactory = factory;
        this.extractUseCase = null; // created lazily
    }

    /**
     * Full-control constructor used primarily in tests to inject a pre-built extractor.
     */
    public AttachmentDocumentResolver(ExtractTextFromDocumentUseCase extractUseCase,
                                      BuildDocumentFromTextUseCase buildUseCase) {
        this.extractUseCase = extractUseCase;
        this.buildUseCase = buildUseCase;
        this.extractUseCaseFactory = null; // never used when an instance is injected directly
    }

    /** Returns the extractor use case, constructing it lazily on first call. */
    private ExtractTextFromDocumentUseCase extractUseCase() {
        ExtractTextFromDocumentUseCase local = extractUseCase;
        if (local == null) {
            synchronized (this) {
                local = extractUseCase;
                if (local == null) {
                    local = extractUseCaseFactory.create();
                    extractUseCase = local;
                }
            }
        }
        return local;
    }

    /**
     * Resolve a Document for the given tab.
     *
     * @param tab the tab to attach
     * @param fallbackName name used for metadata when the tab does not provide one
     * @return a {@link ResolveResult}; never {@code null}
     */
    public ResolveResult resolve(AppTab tab, String fallbackName) {
        if (tab == null) {
            return new ResolveResult(null, Collections.<String>emptyList(),
                    Status.EMPTY_INPUT, "Kein Tab angegeben");
        }

        DocumentSourceTab source = asDocumentSource(tab);

        // 1) Pre-built Document from the preview tab (preferred — ingestion already ran).
        if (source != null) {
            Document preBuilt = source.getDocument();
            if (preBuilt != null && !preBuilt.isEmpty()) {
                return new ResolveResult(preBuilt, copyWarnings(source.getWarnings()),
                        Status.RESOLVED, null);
            }

            // 2) Generic ingestion from raw bytes — handles PDF, DOCX, ... via the registry.
            byte[] rawBytes = source.getRawBytes();
            if (rawBytes != null && rawBytes.length > 0) {
                DocumentMetadata baseMetadata = source.getMetadata();
                String sourceName = resolveSourceName(baseMetadata, fallbackName);
                return ingestFromBytes(rawBytes, sourceName, baseMetadata,
                        copyWarnings(source.getWarnings()));
            }
        }

        // 3) Plain text fallback — only when the content is clearly textual.
        String content = tab.getContent();
        if (content == null || content.trim().isEmpty()) {
            return new ResolveResult(null, Collections.<String>emptyList(),
                    Status.EMPTY_INPUT, "Tab hat keinen Inhalt");
        }
        if (!looksLikeTextContent(content)) {
            LOG.fine("Tab content appears to be binary; refusing to build document from raw text.");
            List<String> warnings = new ArrayList<String>();
            warnings.add("Inhalt sieht wie Binärdaten aus und wurde nicht als Text übernommen");
            return new ResolveResult(null, warnings, Status.BINARY_REJECTED,
                    "Binäre Signatur erkannt; Rohdaten werden nicht als Text angehängt");
        }

        DocumentMetadata metadata = DocumentMetadata.builder()
                .sourceName(fallbackName)
                .build();
        Document document = buildUseCase.buildWithStructure(content, metadata);
        if (document == null || document.isEmpty()) {
            return new ResolveResult(null, Collections.<String>emptyList(),
                    Status.NO_USABLE_CONTENT, "Inhalt konnte nicht zu einem Dokument verarbeitet werden");
        }
        return new ResolveResult(document, Collections.<String>emptyList(), Status.RESOLVED, null);
    }

    private DocumentSourceTab asDocumentSource(AppTab tab) {
        if (tab instanceof DocumentSourceTab) {
            return (DocumentSourceTab) tab;
        }
        if (tab instanceof AttachTabToChatUseCase.DocumentPreviewTabAdapter) {
            return new LegacyAdapterSource((AttachTabToChatUseCase.DocumentPreviewTabAdapter) tab);
        }
        return null;
    }

    private ResolveResult ingestFromBytes(byte[] rawBytes, String sourceName,
                                          DocumentMetadata baseMetadata, List<String> existingWarnings) {
        List<String> warnings = new ArrayList<String>(existingWarnings);
        try {
            // Use the timeout-aware execute(...) path so a corrupt or huge document cannot
            // block the calling thread indefinitely.
            ExtractionResult extraction = extractUseCase().execute(
                    DocumentSource.fromBytes(rawBytes, sourceName)
            );

            if (!extraction.isSuccess()) {
                String error = extraction.getErrorMessage() != null
                        ? extraction.getErrorMessage()
                        : "Textextraktion fehlgeschlagen";
                warnings.add(error);
                LOG.warning("Extraction failed for '" + sourceName + "': " + error);
                return new ResolveResult(null, warnings, Status.EXTRACTION_FAILED, error);
            }

            warnings.addAll(extraction.getWarnings());
            DocumentMetadata metadata = buildMetadata(sourceName, baseMetadata, extraction);
            Document document = buildUseCase.buildWithStructure(extraction.getPlainText(), metadata);
            if (document == null || document.isEmpty()) {
                String reason = "Extraktion erfolgreich, aber kein extrahierbarer Text gefunden";
                warnings.add(reason);
                return new ResolveResult(null, warnings, Status.NO_USABLE_CONTENT, reason);
            }
            return new ResolveResult(document, warnings, Status.RESOLVED, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Ingestion from raw bytes failed for '" + sourceName + "'", e);
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            warnings.add("Textextraktion fehlgeschlagen: " + message);
            return new ResolveResult(null, warnings, Status.EXTRACTION_FAILED, message);
        }
    }

    private DocumentMetadata buildMetadata(String sourceName, DocumentMetadata base, ExtractionResult result) {
        DocumentMetadata.Builder builder = DocumentMetadata.builder()
                .sourceName(firstNonBlank(base != null ? base.getSourceName() : null, sourceName));
        if (base != null) {
            if (base.getMimeType() != null) {
                builder.mimeType(base.getMimeType());
            }
            if (base.getAttributes() != null) {
                builder.attributes(base.getAttributes());
            }
            if (base.getPageCount() != null) {
                builder.pageCount(base.getPageCount());
            }
        }
        if (result != null && result.getMetadata() != null) {
            builder.attributes(result.getMetadata());
            String pageCount = result.getMetadata().get("pageCount");
            if (pageCount != null) {
                try {
                    builder.pageCount(Integer.parseInt(pageCount));
                } catch (NumberFormatException ignored) {
                    // Keep other metadata even if the page count attribute is malformed.
                }
            }
        }
        return builder.build();
    }

    private String resolveSourceName(DocumentMetadata baseMetadata, String fallback) {
        if (baseMetadata != null && baseMetadata.getSourceName() != null
                && !baseMetadata.getSourceName().trim().isEmpty()) {
            return baseMetadata.getSourceName();
        }
        return fallback;
    }

    private List<String> copyWarnings(List<String> sourceWarnings) {
        if (sourceWarnings == null || sourceWarnings.isEmpty()) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(sourceWarnings);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        return second;
    }

    /**
     * Heuristic that rejects content carrying a known unsafe binary signature
     * ({@code %PDF}, ZIP/Office {@code PK\x03\x04}, or embedded NUL bytes). This is not a
     * general-purpose binary detector — its sole purpose is to keep well-known binary
     * payloads out of the textual fallback path. Anything more sophisticated belongs in
     * the ingestion layer.
     */
    static boolean looksLikeTextContent(String content) {
        if (content == null) {
            return false;
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.startsWith("%PDF")) {
            return false;
        }
        if (trimmed.startsWith("PK\u0003\u0004")) { // ZIP/DOCX/XLSX/PPTX
            return false;
        }
        // Embedded NUL bytes are a strong indicator of binary data.
        return content.indexOf('\u0000') < 0;
    }

    /**
     * Status of a resolution attempt, designed so the caller can surface specific feedback.
     */
    public enum Status {
        /** Document was produced and contains usable content. */
        RESOLVED,
        /** Tab provided no content at all (e.g. null/blank). */
        EMPTY_INPUT,
        /** Content was rejected because it carries a known binary signature. */
        BINARY_REJECTED,
        /** Ingestion ran but produced no chunkable text (e.g. scanned PDF without OCR). */
        NO_USABLE_CONTENT,
        /** Extraction failed (extractor error, timeout, exception). */
        EXTRACTION_FAILED
    }

    /**
     * Resolver result.
     * <p>
     * Contains the resolved document (may be {@code null} when nothing usable was found),
     * any warnings emitted during resolution and a {@link Status}/reason describing why
     * resolution succeeded or failed. The reason is intended for user-visible status
     * messages; warnings are intended for the attachment's warnings list.
     */
    public static final class ResolveResult {
        private final Document document;
        private final List<String> warnings;
        private final Status status;
        private final String reason;

        public ResolveResult(Document document, List<String> warnings, Status status, String reason) {
            this.document = document;
            this.warnings = warnings != null
                    ? Collections.unmodifiableList(new ArrayList<String>(warnings))
                    : Collections.<String>emptyList();
            this.status = status != null ? status : Status.NO_USABLE_CONTENT;
            this.reason = reason;
        }

        public Document getDocument() {
            return document;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public Status getStatus() {
            return status;
        }

        public String getReason() {
            return reason;
        }

        public boolean hasDocument() {
            return document != null && !document.isEmpty();
        }
    }

    /** Bridge from the legacy inner adapter to the new port. */
    private static final class LegacyAdapterSource implements DocumentSourceTab {
        private final AttachTabToChatUseCase.DocumentPreviewTabAdapter delegate;

        private LegacyAdapterSource(AttachTabToChatUseCase.DocumentPreviewTabAdapter delegate) {
            this.delegate = delegate;
        }

        @Override
        public Document getDocument() {
            return delegate.getDocument();
        }

        @Override
        public DocumentMetadata getMetadata() {
            return delegate.getMetadata();
        }

        @Override
        public List<String> getWarnings() {
            return delegate.getWarnings();
        }

        @Override
        public byte[] getRawBytes() {
            return delegate.getRawBytes();
        }
    }
}
