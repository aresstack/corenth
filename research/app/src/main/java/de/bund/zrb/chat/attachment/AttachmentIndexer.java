package de.bund.zrb.chat.attachment;

import de.bund.zrb.ingestion.model.document.Document;

/**
 * Port for indexing chat attachments. The default implementation delegates to
 * {@link de.bund.zrb.rag.service.RagService}; tests can substitute a recording fake to
 * verify that {@link AttachTabToChatUseCase} actually drives the indexing path for a
 * resolved attachment Document.
 */
public interface AttachmentIndexer {
    /**
     * Index a document under the given attachment identifier. Implementations must always
     * write to the lexical (BM25) index; embedding generation remains optional.
     */
    void indexAttachment(String attachmentId, String attachmentName, Document document);

    /**
     * Remove an indexed attachment.
     */
    void removeAttachment(String attachmentId);
}
