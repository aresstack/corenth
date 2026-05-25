package de.bund.zrb.chat.attachment;

import de.bund.zrb.ingestion.model.document.Document;
import de.bund.zrb.ingestion.model.document.DocumentMetadata;

import java.util.List;

/**
 * Generic source port for attachments.
 * <p>
 * A {@code DocumentSourceTab} is anything (typically a UI tab) that can hand a chat
 * attachment a pre-built {@link Document} and optional raw bytes. The chat layer depends
 * only on this port; concrete tab implementations (e.g. preview tabs) implement it
 * without leaking file-type-specific knowledge into the chat code.
 * <p>
 * Implementations may return {@code null} from any accessor when the corresponding
 * information is unavailable. {@link #getRawBytes()} should return a defensive copy
 * so callers cannot mutate the tab's internal buffer.
 */
public interface DocumentSourceTab {

    /**
     * @return a pre-built {@link Document} produced by the ingestion pipeline, or
     *         {@code null}/empty if no document is available and the resolver should fall
     *         back to raw bytes / text content.
     */
    Document getDocument();

    /**
     * @return metadata associated with the source, or {@code null}.
     */
    DocumentMetadata getMetadata();

    /**
     * @return warnings produced while loading or ingesting the source, never {@code null}.
     */
    List<String> getWarnings();

    /**
     * @return a defensive copy of the raw bytes backing this source, or {@code null} when
     *         the source is purely textual or no longer holds the original bytes.
     */
    byte[] getRawBytes();

    /**
     * Returns {@code true} when this source has non-empty raw bytes available.
     * <p>
     * Callers that only need to decide whether raw-byte ingestion is required can use
     * this method instead of {@link #getRawBytes()} to avoid allocating a defensive copy
     * just for the check.
     * <p>
     * The default implementation calls {@link #getRawBytes()} and checks the result.
     * Implementations that hold an internal byte array should override this method to
     * check the array reference/length directly, without copying.
     *
     * @return {@code true} iff {@link #getRawBytes()} would return a non-null, non-empty array.
     */
    default boolean hasRawBytes() {
        byte[] b = getRawBytes();
        return b != null && b.length > 0;
    }
}
