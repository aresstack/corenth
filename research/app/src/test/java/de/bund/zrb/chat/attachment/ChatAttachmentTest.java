package de.bund.zrb.chat.attachment;

import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import de.bund.zrb.ingestion.usecase.BuildDocumentFromTextUseCase;
import de.bund.zrb.rag.config.RagConfig;
import de.bund.zrb.rag.infrastructure.LuceneLexicalIndex;
import de.bund.zrb.rag.infrastructure.MarkdownChunker;
import de.bund.zrb.rag.model.Chunk;
import de.bund.zrb.rag.model.ScoredChunk;
import de.zrb.bund.api.Bookmarkable;
import de.zrb.bund.newApi.ui.AppTab;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Chat Attachment system.
 */
class ChatAttachmentTest {

    private ChatAttachmentStore store;
    private AttachmentContextBuilder contextBuilder;
    private BuildHiddenContextUseCase buildHiddenContextUseCase;

    @BeforeEach
    void setUp() {
        store = new ChatAttachmentStore();
        contextBuilder = new AttachmentContextBuilder();
        buildHiddenContextUseCase = new BuildHiddenContextUseCase(store, contextBuilder);
    }

    // ========== ChatAttachment Tests ==========

    @Test
    void chatAttachment_buildsCorrectly() {
        Document doc = Document.builder()
                .paragraph("Test content")
                .build();

        ChatAttachment attachment = ChatAttachment.builder()
                .name("test.txt")
                .sourcePath("/path/to/test.txt")
                .document(doc)
                .warnings(Arrays.asList("Warning 1"))
                .build();

        assertNotNull(attachment.getId());
        assertEquals("test.txt", attachment.getName());
        assertEquals("/path/to/test.txt", attachment.getSourcePath());
        assertNotNull(attachment.getDocument());
        assertTrue(attachment.hasWarnings());
        assertEquals(1, attachment.getWarningsCount());
    }

    @Test
    void chatAttachment_displayLabel_formatsCorrectly() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .mimeType("application/pdf")
                .build();
        Document doc = Document.builder()
                .metadata(metadata)
                .paragraph("Content")
                .build();

        ChatAttachment attachment = ChatAttachment.builder()
                .name("report.pdf")
                .document(doc)
                .build();

        String label = attachment.getDisplayLabel();
        assertTrue(label.contains("report.pdf"));
        assertTrue(label.contains("pdf"));
    }

    // ========== ChatAttachmentStore Tests ==========

    @Test
    void store_storesAndRetrievesAttachment() {
        ChatAttachment attachment = ChatAttachment.builder()
                .name("test.txt")
                .document(Document.fromText("Content"))
                .build();

        String id = store.store(attachment);

        assertNotNull(id);
        assertEquals(attachment, store.get(id));
        assertTrue(store.contains(id));
    }

    @Test
    void store_removesAttachment() {
        ChatAttachment attachment = ChatAttachment.builder()
                .name("test.txt")
                .document(Document.fromText("Content"))
                .build();

        String id = store.store(attachment);
        store.remove(id);

        assertNull(store.get(id));
        assertFalse(store.contains(id));
    }

    @Test
    void store_getAllReturnsMultiple() {
        ChatAttachment att1 = ChatAttachment.builder()
                .name("file1.txt")
                .document(Document.fromText("Content 1"))
                .build();
        ChatAttachment att2 = ChatAttachment.builder()
                .name("file2.txt")
                .document(Document.fromText("Content 2"))
                .build();

        store.store(att1);
        store.store(att2);

        List<ChatAttachment> all = store.getAll(Arrays.asList(att1.getId(), att2.getId()));
        assertEquals(2, all.size());
    }

    // ========== AttachmentContextBuilder Tests ==========

    @Test
    void contextBuilder_buildsContextFromSingleAttachment() {
        Document doc = Document.builder()
                .heading(1, "Title")
                .paragraph("This is the content.")
                .build();

        ChatAttachment attachment = ChatAttachment.builder()
                .name("document.md")
                .document(doc)
                .build();

        AttachmentContextBuilder.BuildResult result = contextBuilder.build(Arrays.asList(attachment));

        assertFalse(result.isEmpty());
        assertEquals(1, result.getAttachmentCount());
        assertTrue(result.getContext().contains("ATTACHED DOCUMENTS"));
        assertTrue(result.getContext().contains("document.md"));
        assertTrue(result.getContext().contains("Title"));
        assertTrue(result.getContext().contains("This is the content"));
    }

    @Test
    void contextBuilder_buildsContextFromMultipleAttachments() {
        ChatAttachment att1 = ChatAttachment.builder()
                .name("file1.txt")
                .document(Document.fromText("Content of file 1"))
                .build();
        ChatAttachment att2 = ChatAttachment.builder()
                .name("file2.txt")
                .document(Document.fromText("Content of file 2"))
                .build();

        AttachmentContextBuilder.BuildResult result = contextBuilder.build(Arrays.asList(att1, att2));

        assertEquals(2, result.getAttachmentCount());
        assertTrue(result.getContext().contains("file1.txt"));
        assertTrue(result.getContext().contains("file2.txt"));
        assertTrue(result.getContext().contains("ATTACHMENT 1"));
        assertTrue(result.getContext().contains("ATTACHMENT 2"));
    }

    @Test
    void contextBuilder_truncatesLongContent() {
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            longContent.append("This is a very long line of text that will be repeated. ");
        }

        Document doc = Document.fromText(longContent.toString());
        ChatAttachment attachment = ChatAttachment.builder()
                .name("large.txt")
                .document(doc)
                .build();

        AttachmentConfig config = new AttachmentConfig()
                .setMaxAttachmentCharsPerDoc(1000);
        AttachmentContextBuilder builder = new AttachmentContextBuilder(config);

        AttachmentContextBuilder.BuildResult result = builder.build(Arrays.asList(attachment));

        assertTrue(result.hasTruncations());
        assertTrue(result.getTruncatedCount() > 0);
        assertTrue(result.getContext().contains("ausgelassen") || result.getContext().contains("gekürzt"));
    }

    @Test
    void contextBuilder_returnsEmptyForNoAttachments() {
        AttachmentContextBuilder.BuildResult result = contextBuilder.build(Arrays.asList());

        assertTrue(result.isEmpty());
        assertEquals(0, result.getAttachmentCount());
    }

    // ========== BuildHiddenContextUseCase Tests ==========

    @Test
    void buildHiddenContextUseCase_buildsContextFromStoredAttachments() {
        Document doc = Document.builder()
                .paragraph("Test paragraph content")
                .build();

        ChatAttachment attachment = ChatAttachment.builder()
                .name("test.md")
                .document(doc)
                .build();

        store.store(attachment);

        AttachmentContextBuilder.BuildResult result = buildHiddenContextUseCase.execute(
                Arrays.asList(attachment.getId())
        );

        assertFalse(result.isEmpty());
        assertTrue(result.getContext().contains("test.md"));
        assertTrue(result.getContext().contains("Test paragraph content"));
    }

    @Test
    void buildHiddenContextUseCase_returnsEmptyForMissingIds() {
        AttachmentContextBuilder.BuildResult result = buildHiddenContextUseCase.execute(
                Arrays.asList("non-existent-id")
        );

        assertTrue(result.isEmpty());
    }

    // ========== Integration Tests ==========

    @Test
    void fullWorkflow_attachAndBuildContext() {
        // Simulate attaching a document
        Document doc = Document.builder()
                .heading(2, "Report Title")
                .paragraph("This is the executive summary.")
                .code("java", "System.out.println(\"Hello\");")
                .build();

        ChatAttachment attachment = ChatAttachment.builder()
                .name("report.md")
                .document(doc)
                .build();

        // Store it
        store.store(attachment);

        // Build context
        AttachmentContextBuilder.BuildResult result = buildHiddenContextUseCase.execute(
                Arrays.asList(attachment.getId())
        );

        // Verify
        String context = result.getContext();
        assertTrue(context.contains("--- ATTACHED DOCUMENTS ---"));
        assertTrue(context.contains("report.md"));
        assertTrue(context.contains("Report Title"));
        assertTrue(context.contains("executive summary"));
        assertTrue(context.contains("System.out.println"));
        assertTrue(context.contains("--- END ATTACHED DOCUMENTS ---"));
    }

    // ========== Generic-Ingestion / PDF Tests ==========

    /**
     * Verifies the issue requirement: AttachTabToChatUseCase must not contain any direct
     * dependency on PdfTextExtractor and must not detect PDFs by name / MIME / %PDF.
     */
    @Test
    void attachTabUseCase_hasNoPdfSpecificDependencies() {
        // Source-level guarantee: imports must not reference any PDF-specific extractor.
        // We assert it indirectly by checking the class' declared fields and methods.
        for (java.lang.reflect.Field f : AttachTabToChatUseCase.class.getDeclaredFields()) {
            String typeName = f.getType().getName().toLowerCase();
            assertFalse(typeName.contains("pdf"),
                    "AttachTabToChatUseCase must not hold PDF-specific fields, but found: " + typeName);
        }
        for (java.lang.reflect.Method m : AttachTabToChatUseCase.class.getDeclaredMethods()) {
            assertFalse(m.getName().toLowerCase().contains("pdf"),
                    "AttachTabToChatUseCase must not declare PDF-specific methods, found: " + m.getName());
        }
    }

    /**
     * Hidden context safety net: even when {@code tab.getContent()} returns raw PDF stream
     * tokens, none of those tokens may ever appear in the hidden context, and the resolver
     * must refuse to build a document from such binary content (no rawBytes available).
     * <p>
     * The use case must also surface a {@code BINARY_REJECTED} status with a reason and
     * a warning so the UI can show a meaningful message instead of a generic "no content".
     */
    @Test
    void hiddenContext_neverContainsRawPdfTokensWhenOnlyBinaryTextIsAvailable() {
        String rawPdfLikeContent = "%PDF-1.4\n1 0 obj\n<< /Font << /F1 1 0 R >> /FlateDecode >>\n"
                + "stream\nbinary-data-here\nendstream\nxref\n0 1\ntrailer\n%%EOF";
        AppTab tab = new TextOnlyTab("dump.pdf", rawPdfLikeContent);

        RecordingIndexer indexer = new RecordingIndexer();
        AttachTabToChatUseCase useCase = new AttachTabToChatUseCase(store, null, indexer);
        AttachTabToChatUseCase.AttachResult result = useCase.executeWithResult(tab);

        // The resolver must refuse — no document is built from a %PDF text stream.
        assertNull(result.getAttachment(),
                "Resolver must not build a Document from raw PDF stream text when no rawBytes are provided");
        assertEquals(AttachmentDocumentResolver.Status.BINARY_REJECTED, result.getStatus(),
                "Status must explain that the content was rejected as binary");
        assertNotNull(result.getReason(), "Reason must be set so the UI can show a meaningful message");
        assertFalse(result.getWarnings().isEmpty(),
                "Warnings must be propagated (would otherwise be silently dropped)");
        assertEquals(0, indexer.indexCalls.size(), "Nothing must reach the indexer when rejected");
    }

    /**
     * Acceptance criterion: a textual PDF (with rawBytes) is attached, its text gets
     * extracted via the generic ingestion path, and the hidden context contains the
     * extracted text — never the raw PDF stream tokens.
     */
    @Test
    void attachPdfTab_extractsTextViaIngestionAndNoRawPdfTokensLeakIntoContext() throws Exception {
        byte[] pdfBytes = createTextPdf("Prozessbeschreibung fuer HEAD");
        // Even if the textual content of the tab is a PDF dump, the rawBytes path takes over.
        String rawPdfLikeContent = "%PDF-1.4\n/Font /FlateDecode\nstream\nbinary-data\nendstream";
        PdfTab tab = new PdfTab("process.pdf", rawPdfLikeContent, pdfBytes);

        AttachTabToChatUseCase useCase = new AttachTabToChatUseCase(store);
        ChatAttachment attachment = useCase.execute(tab);

        assertNotNull(attachment, "Generic ingestion must produce a Document for a text PDF");

        AttachmentContextBuilder.BuildResult result = buildHiddenContextUseCase.execute(
                Collections.singletonList(attachment.getId())
        );

        String context = result.getContext();
        assertTrue(context.contains("Prozessbeschreibung fuer HEAD"),
                "Hidden context must contain the extracted PDF text");
        assertFalse(context.contains("%PDF"), "Hidden context must not contain %PDF");
        assertFalse(context.contains("/FlateDecode"), "Hidden context must not contain /FlateDecode");
        assertFalse(context.contains("/Font"), "Hidden context must not contain /Font");
        assertFalse(context.contains("endstream"), "Hidden context must not contain endstream");
        assertFalse(context.contains("xref"), "Hidden context must not contain xref");
    }

    /**
     * Acceptance criterion: with embeddings and reranker both disabled (lexical-only path),
     * a PDF-derived Document still ends up indexed in Lucene/BM25 and is retrievable.
     * <p>
     * This drives the real attachment integration path: {@code AttachTabToChatUseCase}
     * is invoked with a recording {@link AttachmentIndexer}, and the captured Document is
     * then handed to {@link MarkdownChunker} + {@link LuceneLexicalIndex} — the same
     * components {@code RagService.indexDocument} uses unconditionally for the BM25 path
     * (embeddings stay optional and are gated separately inside RagService).
     */
    @Test
    void pdfAttachment_indexesLexicallyEvenWithEmbeddingsAndRerankerDisabled() throws Exception {
        byte[] pdfBytes = createTextPdf("Unique-attachment-term-Sphinx-of-Quartz");
        PdfTab tab = new PdfTab("doc.pdf", "%PDF-1.4 binary-blob", pdfBytes);

        RecordingIndexer indexer = new RecordingIndexer();
        AttachTabToChatUseCase useCase = new AttachTabToChatUseCase(store, null, indexer);

        AttachTabToChatUseCase.AttachResult result = useCase.executeWithResult(tab);

        assertTrue(result.isSuccess(), "Attachment must succeed for a text PDF");
        ChatAttachment attachment = result.getAttachment();
        assertNotNull(attachment);

        // The use case must drive the indexer with the resolved Document — this verifies
        // the real wiring path through AttachTabToChatUseCase, not a hand-rolled chunker.
        assertEquals(1, indexer.indexCalls.size(),
                "AttachTabToChatUseCase must drive AttachmentIndexer exactly once");
        RecordingIndexer.Call call = indexer.indexCalls.get(0);
        assertEquals(attachment.getId(), call.documentId);
        assertEquals(attachment.getName(), call.documentName);
        assertSame(attachment.getDocument(), call.document,
                "The indexer must receive the same Document that the attachment carries");

        // Replay the lexical-only path that RagService.indexDocument runs unconditionally.
        RagConfig config = new RagConfig().setChunkSizeChars(200).setOverlapChars(20);
        MarkdownChunker chunker = new MarkdownChunker(config);
        LuceneLexicalIndex lexicalIndex = new LuceneLexicalIndex();
        try {
            de.bund.zrb.ingestion.infrastructure.render.RendererRegistry registry =
                    de.bund.zrb.ingestion.infrastructure.render.RendererRegistry.createDefault();
            de.bund.zrb.ingestion.usecase.RenderDocumentUseCase renderUseCase =
                    new de.bund.zrb.ingestion.usecase.RenderDocumentUseCase(registry);
            String markdown = renderUseCase.renderToMarkdown(call.document);

            List<Chunk> chunks = chunker.chunkMarkdown(markdown, call.documentId, call.documentName,
                    "application/pdf");
            assertFalse(chunks.isEmpty(), "Chunker must always produce chunks for a non-empty document");

            lexicalIndex.indexChunks(chunks);

            List<ScoredChunk> hits = lexicalIndex.search("Sphinx", 5);
            assertFalse(hits.isEmpty(),
                    "Lexical/BM25 retrieval must return chunks even when embeddings/reranker are disabled");
            boolean matchesDocument = false;
            for (ScoredChunk hit : hits) {
                if (call.documentId.equals(hit.getChunk().getDocumentId())) {
                    matchesDocument = true;
                    break;
                }
            }
            assertTrue(matchesDocument, "Retrieved chunk must belong to the indexed PDF attachment");
        } finally {
            lexicalIndex.close();
        }
    }

    /**
     * When extraction fails (or yields no usable text), warnings and a reason must reach the
     * caller so the UI can show a meaningful message instead of a generic "kein Inhalt".
     */
    @Test
    void executeWithResult_propagatesWarningsAndReasonOnExtractionFailure() {
        // Invalid PDF bytes — the PDF extractor will report a failure.
        byte[] invalidPdfBytes = "%PDF-not-actually-valid-bytes".getBytes();
        PdfTab tab = new PdfTab("broken.pdf", "%PDF-1.4 bad", invalidPdfBytes);

        AttachTabToChatUseCase useCase = new AttachTabToChatUseCase(store);
        AttachTabToChatUseCase.AttachResult result = useCase.executeWithResult(tab);

        assertNull(result.getAttachment(), "No attachment must be created when extraction fails");
        assertTrue(
                result.getStatus() == AttachmentDocumentResolver.Status.EXTRACTION_FAILED
                        || result.getStatus() == AttachmentDocumentResolver.Status.NO_USABLE_CONTENT,
                "Status must distinguish extraction failure from empty input; got " + result.getStatus());
        assertNotNull(result.getReason(),
                "Reason must be set so the UI can show what went wrong");
        assertFalse(result.getWarnings().isEmpty(),
                "Warnings must be propagated up to the use case caller");
    }

    /**
     * The use case must build a {@link AttachmentDocumentResolver} lazily — sessions that
     * never attach binary documents must not pay the ingestion-stack initialization cost.
     */
    @Test
    void attachTabUseCase_doesNotEagerlyConstructResolver() throws Exception {
        AttachTabToChatUseCase useCase = new AttachTabToChatUseCase(store);
        java.lang.reflect.Field f = AttachTabToChatUseCase.class.getDeclaredField("documentResolver");
        f.setAccessible(true);
        assertNull(f.get(useCase),
                "Default constructor must not eagerly allocate the ingestion stack");
    }

    /**
     * Resolving a plain-text tab must not trigger construction of
     * {@link de.bund.zrb.ingestion.usecase.ExtractTextFromDocumentUseCase} inside
     * {@link AttachmentDocumentResolver}: the expensive Tika/extractor stack should stay
     * uninitialised for text-only attachments.
     */
    @Test
    void resolver_textOnlyResolve_avoidsExtractorInitialization() {
        // Track whether the factory was called.
        final boolean[] factoryCalled = {false};
        AttachmentDocumentResolver.ExtractUseCaseFactory trackingFactory =
                new AttachmentDocumentResolver.ExtractUseCaseFactory() {
                    @Override
                    public de.bund.zrb.ingestion.usecase.ExtractTextFromDocumentUseCase create() {
                        factoryCalled[0] = true;
                        return new de.bund.zrb.ingestion.usecase.ExtractTextFromDocumentUseCase();
                    }
                };

        AttachmentDocumentResolver resolver =
                new AttachmentDocumentResolver(trackingFactory, new BuildDocumentFromTextUseCase());

        // Use the existing TextOnlyTab test double — no raw bytes, no pre-built document.
        AppTab textOnlyTab = new TextOnlyTab("text-tab", "Hello world");

        AttachmentDocumentResolver.ResolveResult result =
                resolver.resolve(textOnlyTab, "text-tab");

        assertFalse(factoryCalled[0],
                "ExtractTextFromDocumentUseCase factory must not be invoked for a text-only tab");
        assertEquals(AttachmentDocumentResolver.Status.RESOLVED, result.getStatus(),
                "Plain text tab should resolve successfully without the extractor");
    }

    /**
     * Defensive copy: the contract for {@link DocumentSourceTab#getRawBytes()} is that
     * callers must not be able to mutate the source tab's internal byte buffer.
     * <p>
     * This test verifies the contract against the production implementation
     * {@link de.bund.zrb.ui.preview.SplitPreviewTab}: after calling
     * {@link de.bund.zrb.ui.preview.SplitPreviewTab#setRawBytes}, every
     * {@code getRawBytes()} call must return a fresh array; mutating one returned copy
     * must not affect subsequent calls.
     */
    @Test
    void documentSourceTab_getRawBytes_isDefensiveCopy_forSplitPreviewTab() {
        // Build a minimal SplitPreviewTab — a text file so no PDF rendering is triggered.
        de.bund.zrb.ui.preview.SplitPreviewTab tab =
                new de.bund.zrb.ui.preview.SplitPreviewTab(
                        "test.txt", "hello world", null, null, null, false);

        byte[] original = new byte[]{10, 20, 30, 40};
        tab.setRawBytes(original.clone()); // clone so the tab has its own copy

        byte[] first = tab.getRawBytes();
        byte[] second = tab.getRawBytes();

        assertNotNull(first, "getRawBytes must not return null after setRawBytes");
        assertNotSame(first, second, "Each getRawBytes() call must return a new array (defensive copy)");

        // Mutating a returned copy must not change subsequent calls.
        first[0] = 99;
        byte[] third = tab.getRawBytes();
        assertEquals(10, third[0],
                "Mutating a returned array must not corrupt the tab's internal buffer");
    }

    /** Recording {@link AttachmentIndexer} test double. */
    private static final class RecordingIndexer implements AttachmentIndexer {
        static final class Call {
            final String documentId;
            final String documentName;
            final Document document;
            Call(String documentId, String documentName, Document document) {
                this.documentId = documentId;
                this.documentName = documentName;
                this.document = document;
            }
        }
        final List<Call> indexCalls = new ArrayList<Call>();
        final List<String> removeCalls = new ArrayList<String>();

        @Override
        public void indexAttachment(String attachmentId, String attachmentName, Document document) {
            indexCalls.add(new Call(attachmentId, attachmentName, document));
        }

        @Override
        public void removeAttachment(String attachmentId) {
            removeCalls.add(attachmentId);
        }
    }

    /**
     * Safety-net coverage for the resolver helper: looksLikeTextContent must reject
     * payloads that start with binary signatures, and accept ordinary text.
     */
    @Test
    void resolver_looksLikeTextContent_rejectsBinarySignatures() {
        assertFalse(AttachmentDocumentResolver.looksLikeTextContent(null));
        assertFalse(AttachmentDocumentResolver.looksLikeTextContent(""));
        assertFalse(AttachmentDocumentResolver.looksLikeTextContent("   \n\t  "));
        assertFalse(AttachmentDocumentResolver.looksLikeTextContent("%PDF-1.4\n..."));
        assertFalse(AttachmentDocumentResolver.looksLikeTextContent("PK\u0003\u0004zipdata"));
        assertFalse(AttachmentDocumentResolver.looksLikeTextContent("hello\u0000world"));
        assertTrue(AttachmentDocumentResolver.looksLikeTextContent("Hello world"));
        assertTrue(AttachmentDocumentResolver.looksLikeTextContent("# Heading\n\nText paragraph."));
    }

    private static final int TEST_PDF_FONT_SIZE = 12;
    private static final float TEST_PDF_LEFT_MARGIN = 72f;
    private static final float TEST_PDF_TOP_POSITION = 720f;

    private static byte[] createTextPdf(String text) throws IOException {
        PDDocument document = new PDDocument();
        try {
            PDPage page = new PDPage();
            document.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            try {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, TEST_PDF_FONT_SIZE);
                contentStream.newLineAtOffset(TEST_PDF_LEFT_MARGIN, TEST_PDF_TOP_POSITION);
                contentStream.showText(text);
                contentStream.endText();
            } finally {
                contentStream.close();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } finally {
            document.close();
        }
    }

    /** Test double for a tab that only carries textual content (no rawBytes, no Document). */
    private static class TextOnlyTab implements AppTab {
        private final String name;
        private final String content;

        private TextOnlyTab(String name, String content) {
            this.name = name;
            this.content = content;
        }

        @Override public String getContent() { return content; }
        @Override public void markAsChanged() { }
        @Override public String getPath() { return name; }
        @Override public Bookmarkable.Type getType() { return Bookmarkable.Type.FILE; }
        @Override public String getTitle() { return name; }
        @Override public String getTooltip() { return name; }
        @Override public JComponent getComponent() { return new JPanel(); }
        @Override public void onClose() { }
        @Override public void saveIfApplicable() { }
        @Override public void focusSearchField() { }
        @Override public void searchFor(String searchPattern) { }
    }

    /** Test double for a preview tab with raw bytes but no pre-built Document. */
    private static class PdfTab implements AppTab, DocumentSourceTab {
        private final String name;
        private final String content;
        private final byte[] rawBytes;
        private final DocumentMetadata metadata;

        private PdfTab(String name, String content, byte[] rawBytes) {
            this.name = name;
            this.content = content;
            this.rawBytes = rawBytes;
            this.metadata = DocumentMetadata.builder()
                    .sourceName(name)
                    .mimeType("application/pdf")
                    .build();
        }

        @Override public String getContent() { return content; }
        @Override public void markAsChanged() { }
        @Override public String getPath() { return name; }
        @Override public Bookmarkable.Type getType() { return Bookmarkable.Type.PREVIEW; }
        @Override public String getTitle() { return name; }
        @Override public String getTooltip() { return name; }
        @Override public JComponent getComponent() { return new JPanel(); }
        @Override public void onClose() { }
        @Override public void saveIfApplicable() { }
        @Override public void focusSearchField() { }
        @Override public void searchFor(String searchPattern) { }

        // Pre-built document is intentionally empty so the resolver falls through to
        // the generic ingestion-from-bytes path.
        @Override public Document getDocument() { return Document.builder().metadata(metadata).build(); }
        @Override public DocumentMetadata getMetadata() { return metadata; }
        @Override public List<String> getWarnings() { return Collections.emptyList(); }
        @Override public byte[] getRawBytes() { return rawBytes != null ? rawBytes.clone() : null; }
    }
}

