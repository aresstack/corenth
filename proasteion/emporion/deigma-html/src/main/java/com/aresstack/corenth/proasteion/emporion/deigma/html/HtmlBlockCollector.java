package com.aresstack.corenth.proasteion.emporion.deigma.html;

import com.aresstack.corenth.proasteion.emporion.deigma.BlockKind;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedBlock;
import com.aresstack.corenth.proasteion.emporion.deigma.ExtractedDocument;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeFilter;
import org.jsoup.select.NodeTraversor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps a parsed HTML document onto ordered Deigma blocks.
 *
 * <p>Document metadata comes first as text-less {@code METADATA} blocks (so it never enters
 * {@link ExtractedDocument#combinedText()}), followed by the visible body content in document
 * order. Inline content between block-level boundaries becomes one block; the traversal is
 * iterative, so deeply nested markup cannot exhaust the stack.
 */
final class HtmlBlockCollector {

    /** Elements whose content is never visible text. */
    private static final String NON_VISIBLE_ELEMENTS =
            "script, style, noscript, template, nav, iframe, object, embed, svg, canvas, [hidden]";

    private static final String[] METADATA_NAMES = {"description", "keywords", "author"};

    private static final Set<String> BLOCK_ELEMENTS = new HashSet<String>(Arrays.asList(
            "address", "article", "aside", "blockquote", "body", "caption", "center", "dd", "details",
            "dialog", "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form", "header",
            "hgroup", "hr", "legend", "li", "main", "ol", "p", "section", "summary", "ul",
            "h1", "h2", "h3", "h4", "h5", "h6", "pre", "table"));

    private final ExtractedDocument.Builder document;
    private final StringBuilder inline = new StringBuilder();
    private final Deque<Boolean> openLists = new ArrayDeque<Boolean>();
    private int openListItems;
    private int headingLevel;
    private int nextIndex;
    private int visibleBlocks;

    HtmlBlockCollector(ExtractedDocument.Builder document) {
        this.document = document;
    }

    /** Adds the metadata and body blocks of the parsed document to the builder. */
    void collect(Document html) {
        collectMetadata(html);
        html.select(NON_VISIBLE_ELEMENTS).remove();
        NodeTraversor.filter(new BodyFilter(), html.body());
        flush();
    }

    /** Returns the number of non-metadata blocks added so far. */
    int visibleBlockCount() {
        return visibleBlocks;
    }

    private void collectMetadata(Document html) {
        addMetadata("charset", html.charset().name());
        Element root = html.selectFirst("html");
        if (root != null) {
            String language = root.hasAttr("lang") ? root.attr("lang") : root.attr("xml:lang");
            addMetadata("language", language);
        }
        for (String name : METADATA_NAMES) {
            Element meta = html.selectFirst("meta[name=" + name + "]");
            if (meta != null) {
                addMetadata(name, meta.attr("content"));
            }
        }
    }

    private void addMetadata(String name, String value) {
        String normalized = normalizeInline(value == null ? "" : value);
        if (normalized.isEmpty()) {
            return;
        }
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        attributes.put("name", name);
        attributes.put("value", normalized);
        document.addBlock(new ExtractedBlock(nextIndex++, BlockKind.METADATA, null, attributes));
    }

    private void addVisible(BlockKind kind, String text, Map<String, String> attributes) {
        document.addBlock(new ExtractedBlock(nextIndex++, kind, text, attributes));
        visibleBlocks++;
    }

    private void flush() {
        String text = normalizeInline(inline.toString());
        inline.setLength(0);
        if (text.isEmpty()) {
            return;
        }
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        if (headingLevel > 0) {
            attributes.put("level", String.valueOf(headingLevel));
            addVisible(BlockKind.HEADING, text, attributes);
        } else if (openListItems > 0) {
            Boolean ordered = openLists.peek();
            attributes.put("ordered", String.valueOf(ordered != null && ordered.booleanValue()));
            attributes.put("depth", String.valueOf(Math.max(1, openLists.size())));
            addVisible(BlockKind.LIST, text, attributes);
        } else {
            addVisible(BlockKind.TEXT, text, null);
        }
    }

    private void appendText(String raw) {
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            inline.append(isSpace(c) ? ' ' : c);
        }
    }

    private void addCode(Element pre) {
        String text = pre.wholeText().replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("\n")) {
            text = text.substring(1);
        }
        text = stripTrailingWhitespace(text);
        if (text.trim().isEmpty()) {
            return;
        }
        String language = codeLanguage(pre);
        Map<String, String> attributes = language == null ? null : Collections.singletonMap("language", language);
        addVisible(BlockKind.CODE, text, attributes);
    }

    private void addTable(Element table) {
        List<String> rows = new ArrayList<String>();
        int columns = 0;
        for (Element row : table.getElementsByTag("tr")) {
            if (owningTable(row) != table) {
                continue;
            }
            List<String> cells = new ArrayList<String>();
            boolean hasText = false;
            for (Element cell : row.children()) {
                if ("td".equals(cell.normalName()) || "th".equals(cell.normalName())) {
                    String text = cell.text().trim();
                    hasText |= !text.isEmpty();
                    cells.add(text);
                }
            }
            if (hasText) {
                rows.add(join(cells, '\t'));
                columns = Math.max(columns, cells.size());
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        attributes.put("rows", String.valueOf(rows.size()));
        attributes.put("columns", String.valueOf(columns));
        Element caption = table.selectFirst("caption");
        if (caption != null && owningTable(caption) == table && !caption.text().trim().isEmpty()) {
            attributes.put("caption", caption.text().trim());
        }
        addVisible(BlockKind.TABLE, join(rows, '\n'), attributes);
    }

    private static Element owningTable(Element element) {
        Element parent = element.parent();
        while (parent != null && !"table".equals(parent.normalName())) {
            parent = parent.parent();
        }
        return parent;
    }

    private static String codeLanguage(Element pre) {
        String language = languageFromClasses(pre);
        if (language != null) {
            return language;
        }
        for (Element child : pre.children()) {
            if ("code".equals(child.normalName())) {
                language = languageFromClasses(child);
                if (language != null) {
                    return language;
                }
            }
        }
        return null;
    }

    private static String languageFromClasses(Element element) {
        for (String className : element.classNames()) {
            if (className.startsWith("language-") && className.length() > "language-".length()) {
                return className.substring("language-".length());
            }
            if (className.startsWith("lang-") && className.length() > "lang-".length()) {
                return className.substring("lang-".length());
            }
        }
        return null;
    }

    private static int headingLevelOf(String name) {
        if (name.length() == 2 && name.charAt(0) == 'h' && name.charAt(1) >= '1' && name.charAt(1) <= '6') {
            return name.charAt(1) - '0';
        }
        return 0;
    }

    private static boolean isList(String name) {
        return "ul".equals(name) || "ol".equals(name);
    }

    private static boolean isSpace(char c) {
        return c == ' ' || Character.isWhitespace(c);
    }

    /** Collapses whitespace runs, keeping line breaks that came from {@code <br>}. */
    private static String normalizeInline(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (String line : text.split("\n", -1)) {
            if (result.length() > 0 || line.length() > 0) {
                result.append(collapseSpaces(line)).append('\n');
            }
        }
        return result.toString().trim();
    }

    private static String collapseSpaces(String line) {
        StringBuilder result = new StringBuilder(line.length());
        boolean pendingSpace = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (isSpace(c)) {
                pendingSpace = result.length() > 0;
            } else {
                if (pendingSpace) {
                    result.append(' ');
                    pendingSpace = false;
                }
                result.append(c);
            }
        }
        return result.toString();
    }

    private static String stripTrailingWhitespace(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    private static String join(List<String> parts, char separator) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                result.append(separator);
            }
            result.append(parts.get(i));
        }
        return result.toString();
    }

    private final class BodyFilter implements NodeFilter {

        @Override
        public FilterResult head(Node node, int depth) {
            if (node instanceof TextNode) {
                appendText(((TextNode) node).getWholeText());
                return FilterResult.CONTINUE;
            }
            if (!(node instanceof Element)) {
                return FilterResult.CONTINUE;
            }
            String name = ((Element) node).normalName();
            if ("br".equals(name)) {
                inline.append('\n');
                return FilterResult.CONTINUE;
            }
            if (!BLOCK_ELEMENTS.contains(name)) {
                return FilterResult.CONTINUE;
            }
            flush();
            if ("pre".equals(name)) {
                addCode((Element) node);
                return FilterResult.SKIP_ENTIRELY;
            }
            if ("table".equals(name)) {
                addTable((Element) node);
                return FilterResult.SKIP_ENTIRELY;
            }
            int level = headingLevelOf(name);
            if (level > 0) {
                headingLevel = level;
            } else if (isList(name)) {
                openLists.push(Boolean.valueOf("ol".equals(name)));
            } else if ("li".equals(name)) {
                openListItems++;
            }
            return FilterResult.CONTINUE;
        }

        @Override
        public FilterResult tail(Node node, int depth) {
            if (!(node instanceof Element)) {
                return FilterResult.CONTINUE;
            }
            String name = ((Element) node).normalName();
            if (!BLOCK_ELEMENTS.contains(name)) {
                return FilterResult.CONTINUE;
            }
            flush();
            if (headingLevelOf(name) > 0) {
                headingLevel = 0;
            } else if (isList(name)) {
                openLists.poll();
            } else if ("li".equals(name)) {
                openListItems = Math.max(0, openListItems - 1);
            }
            return FilterResult.CONTINUE;
        }
    }
}
