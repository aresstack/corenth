package com.aresstack.corenth.proasteion.emporion.deigma.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;

/**
 * Opens a parsed PDF from raw bytes; a seam that lets tests observe document cleanup.
 */
interface PdfDocumentLoader {

    PdfDocumentLoader PDFBOX = new PdfDocumentLoader() {
        @Override
        public PDDocument load(byte[] content) throws IOException {
            return PDDocument.load(content);
        }
    };

    /** Parses the bytes with an empty user password; the caller closes the result. */
    PDDocument load(byte[] content) throws IOException;
}
