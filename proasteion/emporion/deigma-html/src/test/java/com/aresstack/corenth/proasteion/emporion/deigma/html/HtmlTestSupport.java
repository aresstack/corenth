package com.aresstack.corenth.proasteion.emporion.deigma.html;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.proasteion.emporion.deigma.BlockKind;
import com.aresstack.corenth.proasteion.emporion.deigma.ContentCategory;
import com.aresstack.corenth.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedBlock;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedDocument;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractionRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

final class HtmlTestSupport {

    static final Charset UTF_8 = Charset.forName("UTF-8");
    static final DetectedContentType HTML = new DetectedContentType("text/html", ContentCategory.HTML, "page.html");

    private HtmlTestSupport() {
    }

    static VirtualResourceRef ref() {
        return new VirtualResourceRef(BookmarkUri.parse("file:///test/page.html"), VirtualResourceKind.FILE);
    }

    static ExtractionRequest request(byte[] content, String contentTypeHint) {
        return new ExtractionRequest(ref(), content, "page.html", contentTypeHint, HTML);
    }

    static ExtractionRequest request(String html) {
        return request(html.getBytes(UTF_8), null);
    }

    static byte[] fixture(String name) {
        InputStream in = HtmlTestSupport.class.getResourceAsStream(name);
        if (in == null) {
            throw new IllegalStateException("missing fixture " + name);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // test helper
            }
        }
    }

    /** Renders every block as {@code KIND|text|attributes} for compact ordered assertions. */
    static List<String> describe(ExtractedDocument document) {
        List<String> lines = new ArrayList<String>();
        for (ExtractedBlock block : document.blocks()) {
            lines.add(block.kind() + "|" + block.text() + "|" + block.attributes());
        }
        return lines;
    }

    static List<ExtractedBlock> visible(ExtractedDocument document) {
        List<ExtractedBlock> blocks = new ArrayList<ExtractedBlock>();
        for (ExtractedBlock block : document.blocks()) {
            if (block.kind() != BlockKind.METADATA) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    static String metadata(ExtractedDocument document, String name) {
        for (ExtractedBlock block : document.blocks()) {
            if (block.kind() == BlockKind.METADATA && name.equals(block.attributes().get("name"))) {
                return block.attributes().get("value");
            }
        }
        return null;
    }
}
