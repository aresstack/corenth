package com.aresstack.corenth.proasteion.emporion.deigma.office;

import java.io.Closeable;
import java.io.IOException;

/**
 * Opens a parsed Office document from raw bytes; a seam that lets tests observe cleanup.
 *
 * @param <T> the parsed document type; the caller closes it
 */
interface OfficeDocumentLoader<T extends Closeable> {

    T load(byte[] content) throws IOException;
}
