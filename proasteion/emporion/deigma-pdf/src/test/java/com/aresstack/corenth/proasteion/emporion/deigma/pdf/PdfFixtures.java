package com.aresstack.corenth.proasteion.emporion.deigma.pdf;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.proasteion.emporion.deigma.ContentCategory;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Generates minimal PDFs in memory so no binary fixture is checked in.
 */
final class PdfFixtures {

    static final DetectedContentType PDF = new DetectedContentType("application/pdf", ContentCategory.PDF, "doc.pdf");

    private PdfFixtures() {
    }

    static VirtualResourceRef ref() {
        return new VirtualResourceRef(BookmarkUri.parse("file:///test/doc.pdf"), VirtualResourceKind.FILE);
    }

    static ExtractionRequest request(byte[] content) {
        return new ExtractionRequest(ref(), content, "doc.pdf", null, PDF);
    }

    /** Builds a PDF with one page per argument; each {@code \n} starts a new text line, "" is a blank page. */
    static byte[] pages(String... pageTexts) {
        return build(null, null, pageTexts);
    }

    /** Builds a PDF with a filled information dictionary. */
    static byte[] withInformation(String... pageTexts) {
        PDDocumentInformation info = new PDDocumentInformation();
        info.setTitle("  Hafenbericht  ");
        info.setAuthor("Corenth Tests");
        info.setSubject("Deigma");
        info.setKeywords("hafen, fracht");
        info.setCreator("PdfFixtures");
        info.setProducer("PDFBox");
        Calendar created = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        created.clear();
        created.set(2026, Calendar.OCTOBER, 7, 1, 2, 3);
        info.setCreationDate(created);
        return build(info, null, pageTexts);
    }

    static byte[] protectedWith(String ownerPassword, String userPassword, boolean canExtract, String... pageTexts) {
        AccessPermission permission = new AccessPermission();
        permission.setCanExtractContent(canExtract);
        StandardProtectionPolicy policy = new StandardProtectionPolicy(ownerPassword, userPassword, permission);
        policy.setEncryptionKeyLength(128);
        return build(null, policy, pageTexts);
    }

    private static byte[] build(PDDocumentInformation info, StandardProtectionPolicy policy, String... pageTexts) {
        PDDocument document = new PDDocument();
        try {
            if (info != null) {
                document.setDocumentInformation(info);
            }
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (pageText.isEmpty()) {
                    continue;
                }
                PDPageContentStream stream = new PDPageContentStream(document, page);
                try {
                    stream.beginText();
                    stream.setFont(PDType1Font.HELVETICA, 12);
                    stream.setLeading(16);
                    stream.newLineAtOffset(72, 700);
                    for (String line : pageText.split("\n")) {
                        stream.showText(line);
                        stream.newLine();
                    }
                    stream.endText();
                } finally {
                    stream.close();
                }
            }
            if (policy != null) {
                document.protect(policy);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                document.close();
            } catch (IOException ignored) {
                // test fixture
            }
        }
    }
}
