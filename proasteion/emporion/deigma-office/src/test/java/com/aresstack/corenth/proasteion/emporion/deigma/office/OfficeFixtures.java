package com.aresstack.corenth.proasteion.emporion.deigma.office;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.proasteion.emporion.deigma.ContentCategory;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedBlock;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedDocument;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds minimal, license-safe Office fixtures in memory so no binary file is checked in.
 */
final class OfficeFixtures {

    static final Charset UTF_8 = Charset.forName("UTF-8");
    static final DetectedContentType DOCX = new DetectedContentType(
            DocxDocumentExtractor.DOCX_MIME_TYPE, ContentCategory.OFFICE_DOCUMENT, "bericht.docx");
    static final DetectedContentType XLSX = new DetectedContentType(
            XlsxDocumentExtractor.XLSX_MIME_TYPE, ContentCategory.OFFICE_DOCUMENT, "zoelle.xlsx");

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships";

    private OfficeFixtures() {
    }

    static VirtualResourceRef ref() {
        return new VirtualResourceRef(BookmarkUri.parse("file:///test/office"), VirtualResourceKind.FILE);
    }

    static ExtractionRequest request(byte[] content, DetectedContentType type) {
        return new ExtractionRequest(ref(), content, type.filenameHint(), null, type);
    }

    static List<String> describe(ExtractedDocument document) {
        List<String> lines = new ArrayList<String>();
        for (ExtractedBlock block : document.blocks()) {
            lines.add(block.kind() + "|" + block.text() + "|" + block.attributes());
        }
        return lines;
    }

    /**
     * A WordprocessingML package with heading, paragraph, bullet and numbered lists,
     * a table, core properties and one embedded workbook part.
     */
    static byte[] reportDocx() {
        String body = ""
                + heading("Ankunft im Hafen", 1)
                + paragraph("Die Holkas liefert   Fracht.")
                + paragraph("")
                + heading("Ladung", 2)
                + listItem("Bronze", 1, 0)
                + listItem("Wein", 1, 0)
                + listItem("Rotwein", 2, 1)
                + listItem("Erster Schritt", 2, 0)
                + "<w:tbl>"
                + row("Ware", "Zoll")
                + row("Bronze", "5")
                + row("", "")
                + row("Wein", "")
                + "</w:tbl>"
                + paragraph("Ende.");
        return docx(body, true, true);
    }

    static byte[] docxWithBody(String bodyXml) {
        return docx(bodyXml, false, false);
    }

    static String paragraph(String text) {
        return "<w:p><w:r><w:t xml:space=\"preserve\">" + text + "</w:t></w:r></w:p>";
    }

    static String heading(String text, int level) {
        return "<w:p><w:pPr><w:pStyle w:val=\"Heading" + level + "\"/></w:pPr><w:r><w:t>" + text + "</w:t></w:r></w:p>";
    }

    /** {@code numId} 1 is a bullet list, 2 a decimal list. */
    static String listItem(String text, int numId, int level) {
        return "<w:p><w:pPr><w:numPr><w:ilvl w:val=\"" + level + "\"/><w:numId w:val=\"" + numId + "\"/></w:numPr></w:pPr>"
                + "<w:r><w:t>" + text + "</w:t></w:r></w:p>";
    }

    static String row(String... cells) {
        StringBuilder xml = new StringBuilder("<w:tr>");
        for (String cell : cells) {
            xml.append("<w:tc>").append(paragraph(cell)).append("</w:tc>");
        }
        return xml.append("</w:tr>").toString();
    }

    private static byte[] docx(String bodyXml, boolean withCoreProperties, boolean withEmbedding) {
        Map<String, String> parts = new LinkedHashMap<String, String>();
        parts.put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Default Extension=\"xlsx\" ContentType=\"" + XlsxDocumentExtractor.XLSX_MIME_TYPE + "\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
                + "<Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/>"
                + "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>"
                + "</Types>");
        parts.put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"" + PKG_REL + "\">"
                + "<Relationship Id=\"rId1\" Type=\"" + REL + "/officeDocument\" Target=\"word/document.xml\"/>"
                + (withCoreProperties
                ? "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
                : "")
                + "</Relationships>");
        parts.put("word/_rels/document.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"" + PKG_REL + "\">"
                + "<Relationship Id=\"rId1\" Type=\"" + REL + "/styles\" Target=\"styles.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"" + REL + "/numbering\" Target=\"numbering.xml\"/>"
                + (withEmbedding ? "<Relationship Id=\"rId3\" Type=\"" + REL + "/package\" Target=\"embeddings/Tabelle.xlsx\"/>" : "")
                + "</Relationships>");
        parts.put("word/document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"" + W + "\"><w:body>" + bodyXml + "</w:body></w:document>");
        parts.put("word/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:styles xmlns:w=\"" + W + "\">"
                + "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/></w:style>"
                + "<w:style w:type=\"paragraph\" w:styleId=\"Heading2\"><w:name w:val=\"heading 2\"/></w:style>"
                + "<w:style w:type=\"paragraph\" w:styleId=\"Quote\"><w:name w:val=\"Quote\"/></w:style>"
                + "</w:styles>");
        parts.put("word/numbering.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:numbering xmlns:w=\"" + W + "\">"
                + "<w:abstractNum w:abstractNumId=\"0\">"
                + "<w:lvl w:ilvl=\"0\"><w:numFmt w:val=\"bullet\"/></w:lvl>"
                + "<w:lvl w:ilvl=\"1\"><w:numFmt w:val=\"bullet\"/></w:lvl>"
                + "</w:abstractNum>"
                + "<w:abstractNum w:abstractNumId=\"1\"><w:lvl w:ilvl=\"0\"><w:numFmt w:val=\"decimal\"/></w:lvl></w:abstractNum>"
                + "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num>"
                + "<w:num w:numId=\"2\"><w:abstractNumId w:val=\"1\"/></w:num>"
                + "</w:numbering>");
        if (withCoreProperties) {
            parts.put("docProps/core.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\""
                    + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\""
                    + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                    + "<dc:title> Hafenbericht </dc:title><dc:creator>Corenth Tests</dc:creator>"
                    + "<dc:subject>Deigma</dc:subject><cp:keywords>hafen, fracht</cp:keywords>"
                    + "<dcterms:created xsi:type=\"dcterms:W3CDTF\">2026-10-07T01:02:03Z</dcterms:created>"
                    + "</cp:coreProperties>");
        }
        Map<String, byte[]> binary = new LinkedHashMap<String, byte[]>();
        if (withEmbedding) {
            binary.put("word/embeddings/Tabelle.xlsx", "not inspected".getBytes(UTF_8));
        }
        return zip(parts, binary);
    }

    static byte[] zip(Map<String, String> textParts, Map<String, byte[]> binaryParts) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ZipOutputStream zip = new ZipOutputStream(out);
            for (Map.Entry<String, String> part : textParts.entrySet()) {
                zip.putNextEntry(new ZipEntry(part.getKey()));
                zip.write(part.getValue().getBytes(UTF_8));
                zip.closeEntry();
            }
            for (Map.Entry<String, byte[]> part : binaryParts.entrySet()) {
                zip.putNextEntry(new ZipEntry(part.getKey()));
                zip.write(part.getValue());
                zip.closeEntry();
            }
            zip.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Wraps an OOXML package in a password-protected OLE2 container (standard encryption). */
    static byte[] encrypt(byte[] ooxml, String password) {
        try {
            POIFSFileSystem container = new POIFSFileSystem();
            EncryptionInfo info = new EncryptionInfo(EncryptionMode.standard);
            Encryptor encryptor = info.getEncryptor();
            encryptor.confirmPassword(password);
            OPCPackage opc = OPCPackage.open(new ByteArrayInputStream(ooxml));
            OutputStream encrypted = encryptor.getDataStream(container);
            try {
                opc.save(encrypted);
            } finally {
                encrypted.close();
                opc.revert();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            container.writeFilesystem(out);
            container.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** An empty legacy OLE2 container standing in for a binary .doc/.xls file. */
    static byte[] legacyOle2() {
        try {
            POIFSFileSystem container = new POIFSFileSystem();
            container.createDocument(new ByteArrayInputStream("legacy".getBytes(UTF_8)), "WordDocument");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            container.writeFilesystem(out);
            container.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
