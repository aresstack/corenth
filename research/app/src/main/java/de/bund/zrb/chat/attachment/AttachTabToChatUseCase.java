package de.bund.zrb.chat.attachment;

import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;
import de.bund.zrb.ingestion.usecase.BuildDocumentFromTextUseCase;
import de.bund.zrb.rag.service.RagService;
import de.zrb.bund.newApi.ui.AppTab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Use case for attaching a tab's content to the chat.
 * <p>
 * This class is intentionally <em>format agnostic</em>: it only orchestrates the attachment
 * flow and delegates any document-type-specific work (PDF text extraction, DOCX parsing, ...)
 * to the ingestion layer via {@link AttachmentDocumentResolver}. PDF detection,
 * {@code PdfTextExtractor} or any reference to {@code %PDF}, MIME types or {@code .pdf}
 * filenames must <strong>not</strong> live in this class.
 * <p>
 * Indexing always writes to the lexical (BM25) index via
 * {@link RagService#indexDocumentAsync(String, String, Document)}; embeddings and reranking
 * stay optional and never gate lexical indexing or retrieval.
 */
public class AttachTabToChatUseCase {

    private static final Logger LOG = Logger.getLogger(AttachTabToChatUseCase.class.getName());

    private final ChatAttachmentStore store;
    /**
     * Lazily allocated resolver — instantiating {@link AttachmentDocumentResolver} pulls in
     * the entire ingestion stack (Tika detector, extractor registry). Sessions that never
     * attach a binary document never pay that cost.
     * <p>
     * Declared {@code volatile} so the double-checked locking idiom in {@link #resolver()}
     * is safe under the Java Memory Model: writes to the resolver field happen-before any
     * subsequent reads in other threads.
     */
    private volatile AttachmentDocumentResolver documentResolver;
    private final BuildDocumentFromTextUseCase buildDocumentUseCase = new BuildDocumentFromTextUseCase();
    private final AttachmentIndexer indexer;

    public AttachTabToChatUseCase() {
        this(ChatAttachmentStore.getInstance());
    }

    public AttachTabToChatUseCase(ChatAttachmentStore store) {
        this(store, null, null);
    }

    public AttachTabToChatUseCase(ChatAttachmentStore store, AttachmentDocumentResolver documentResolver) {
        this(store, documentResolver, null);
    }

    public AttachTabToChatUseCase(ChatAttachmentStore store,
                                  AttachmentDocumentResolver documentResolver,
                                  AttachmentIndexer indexer) {
        this.store = store;
        this.documentResolver = documentResolver; // may be null — created on demand
        this.indexer = indexer != null ? indexer : new RagServiceAttachmentIndexer();
    }

    private AttachmentDocumentResolver resolver() {
        AttachmentDocumentResolver local = documentResolver;
        if (local == null) {
            synchronized (this) {
                local = documentResolver;
                if (local == null) {
                    local = new AttachmentDocumentResolver();
                    documentResolver = local;
                }
            }
        }
        return local;
    }

    /**
     * Result of attaching a tab. Always present so callers can show meaningful feedback
     * even when no attachment was produced (extraction failure, binary content, ...).
     */
    public static final class AttachResult {
        private final ChatAttachment attachment;
        private final AttachmentDocumentResolver.Status status;
        private final String reason;
        private final List<String> warnings;

        public AttachResult(ChatAttachment attachment,
                            AttachmentDocumentResolver.Status status,
                            String reason,
                            List<String> warnings) {
            this.attachment = attachment;
            this.status = status != null ? status : AttachmentDocumentResolver.Status.NO_USABLE_CONTENT;
            this.reason = reason;
            this.warnings = warnings != null
                    ? Collections.unmodifiableList(new ArrayList<String>(warnings))
                    : Collections.<String>emptyList();
        }

        public ChatAttachment getAttachment() {
            return attachment;
        }

        public AttachmentDocumentResolver.Status getStatus() {
            return status;
        }

        public String getReason() {
            return reason;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public boolean isSuccess() {
            return attachment != null && status == AttachmentDocumentResolver.Status.RESOLVED;
        }
    }

    /**
     * Attach a tab to the chat.
     *
     * @param tab the tab to attach
     * @return the created attachment, or {@code null} if no attachment was produced.
     *         For richer feedback (status/reason/warnings) use {@link #executeWithResult(AppTab)}.
     */
    public ChatAttachment execute(AppTab tab) {
        AttachResult result = executeWithResult(tab);
        return result.getAttachment();
    }

    /**
     * Attach a tab to the chat and return a structured {@link AttachResult}.
     * <p>
     * The returned value always exposes the resolution status, reason and any collected
     * warnings — even when no attachment was produced — so the UI can surface a precise
     * message instead of a generic "no content" error.
     */
    public AttachResult executeWithResult(AppTab tab) {
        if (tab == null) {
            return new AttachResult(null, AttachmentDocumentResolver.Status.EMPTY_INPUT,
                    "Kein Tab angegeben", Collections.<String>emptyList());
        }

        String name = tab.getTitle();
        String path = null;

        // Try to get path from Bookmarkable (works for all tab types)
        try {
            path = tab.getPath();
        } catch (Exception ignored) {}

        AttachmentDocumentResolver.ResolveResult resolved = resolver().resolve(tab, name);
        Document document = resolved.getDocument();
        List<String> warnings = resolved.getWarnings();

        if (document == null || document.isEmpty()) {
            if (resolved.getReason() != null) {
                LOG.warning("Tab '" + name + "' could not be attached: " + resolved.getReason());
            } else {
                LOG.warning("Tab '" + name + "' could not be attached (no usable content).");
            }
            for (String warning : warnings) {
                LOG.warning("Attachment warning for '" + name + "': " + warning);
            }
            return new AttachResult(null, resolved.getStatus(), resolved.getReason(), warnings);
        }

        // Try to get the source name from the document metadata for nicer paths.
        if (path == null && document.getMetadata() != null && document.getMetadata().getSourceName() != null) {
            path = document.getMetadata().getSourceName();
        }

        // Create attachment
        ChatAttachment attachment = ChatAttachment.builder()
                .name(name)
                .sourcePath(path)
                .tabId(String.valueOf(System.identityHashCode(tab)))
                .document(document)
                .warnings(warnings)
                .build();

        // Store it
        store.store(attachment);

        // Index in RAG for retrieval — Lucene/BM25 is always written;
        // embeddings only happen when the embedding client is available.
        indexAttachmentAsync(attachment);

        return new AttachResult(attachment, AttachmentDocumentResolver.Status.RESOLVED, null, warnings);
    }

    /**
     * Attach content directly (for testing or programmatic use).
     */
    public ChatAttachment attachContent(String name, String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        if (!AttachmentDocumentResolver.looksLikeTextContent(content)) {
            LOG.warning("Refusing to attach content that looks like binary data: " + name);
            return null;
        }

        DocumentMetadata metadata = DocumentMetadata.builder()
                .sourceName(name)
                .build();
        Document document = buildDocumentUseCase.buildWithStructure(content, metadata);

        ChatAttachment attachment = ChatAttachment.builder()
                .name(name)
                .document(document)
                .build();

        store.store(attachment);

        // Index in RAG for retrieval
        indexAttachmentAsync(attachment);

        return attachment;
    }

    /**
     * Remove an attachment from the store and RAG index.
     */
    public void detach(String attachmentId) {
        store.remove(attachmentId);

        // Remove from RAG index
        try {
            indexer.removeAttachment(attachmentId);
            LOG.fine("Removed attachment from RAG index: " + attachmentId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to remove attachment from RAG: " + attachmentId, e);
        }
    }

    /**
     * Get all current attachments.
     */
    public List<ChatAttachment> getCurrentAttachments() {
        return store.getAllAttachments();
    }

    /**
     * Index an attachment in the RAG system (asynchronously by default).
     */
    private void indexAttachmentAsync(ChatAttachment attachment) {
        if (attachment == null || attachment.getDocument() == null) {
            return;
        }

        try {
            indexer.indexAttachment(attachment.getId(), attachment.getName(), attachment.getDocument());
            LOG.fine("Queued attachment for RAG indexing: " + attachment.getName());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to index attachment in RAG: " + attachment.getName(), e);
        }
    }

    /** Default {@link AttachmentIndexer} that forwards to the {@code RagService} singleton. */
    private static final class RagServiceAttachmentIndexer implements AttachmentIndexer {
        @Override
        public void indexAttachment(String attachmentId, String attachmentName, Document document) {
            RagService.getInstance().indexDocumentAsync(attachmentId, attachmentName, document);
        }

        @Override
        public void removeAttachment(String attachmentId) {
            RagService.getInstance().removeDocument(attachmentId);
        }
    }

    /**
     * @deprecated Use {@link DocumentSourceTab} directly. This nested interface is retained
     * only for binary backwards compatibility with existing UI implementations; new code
     * should depend on {@code DocumentSourceTab} (the dedicated attachment port) so the UI
     * does not transitively pull in the chat use case.
     */
    @Deprecated
    public interface DocumentPreviewTabAdapter extends DocumentSourceTab {
        @Override
        Document getDocument();

        @Override
        DocumentMetadata getMetadata();

        @Override
        List<String> getWarnings();

        /**
         * Raw bytes of the underlying resource, if the source still has them. May be
         * {@code null} for tabs that only hold textual content. Implementations should
         * return a defensive copy so callers cannot mutate the source's internal buffer.
         */
        @Override
        default byte[] getRawBytes() {
            return null;
        }
    }
}
