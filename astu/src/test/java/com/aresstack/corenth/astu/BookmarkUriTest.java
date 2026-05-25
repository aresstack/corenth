package com.aresstack.corenth.astu;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class BookmarkUriTest {

    // ── Standard file: URIs ──────────────────────────────────────────────────────

    @Test
    void parseFileUriWindows() {
        BookmarkUri uri = BookmarkUri.parse("file:///C:/Users/example/Documents/note.txt");
        assertEquals(ResourceScheme.FILE, uri.scheme());
        assertEquals("///C:/Users/example/Documents/note.txt", uri.schemeSpecificPart());
        assertNotNull(uri.toURI());
        assertEquals("/C:/Users/example/Documents/note.txt", uri.toURI().getPath());
    }

    @Test
    void parseFileUriUnc() {
        BookmarkUri uri = BookmarkUri.parse("file://server/share/folder/document.txt");
        assertEquals(ResourceScheme.FILE, uri.scheme());
        assertNotNull(uri.toURI());
        assertEquals("file://server/share/folder/document.txt", uri.toString());
    }

    @Test
    void parseFileUriUnix() {
        BookmarkUri uri = BookmarkUri.parse("file:///home/user/docs/readme.md");
        assertEquals(ResourceScheme.FILE, uri.scheme());
        assertEquals("///home/user/docs/readme.md", uri.schemeSpecificPart());
        assertEquals("/home/user/docs/readme.md", uri.toURI().getPath());
    }

    // ── Legacy local:// normalization ────────────────────────────────────────────

    @Test
    void legacyLocalSchemeNormalizesToFile() {
        BookmarkUri uri = BookmarkUri.parse("local://C:/Users/file.txt");
        assertEquals(ResourceScheme.FILE, uri.scheme());
        // local:// is normalized to file:///
        assertTrue(uri.toString().startsWith("file:///"));
    }

    @Test
    void legacyLocalSchemeCaseInsensitive() {
        BookmarkUri uri = BookmarkUri.parse("LOCAL://docs/file.txt");
        assertEquals(ResourceScheme.FILE, uri.scheme());
    }

    // ── Standard http/https URIs ─────────────────────────────────────────────────

    @Test
    void parseHttpsUri() {
        BookmarkUri uri = BookmarkUri.parse("https://example.internal/wiki/page");
        assertEquals(ResourceScheme.HTTPS, uri.scheme());
        assertEquals("//example.internal/wiki/page", uri.schemeSpecificPart());
        assertNotNull(uri.toURI());
        assertEquals("https://example.internal/wiki/page", uri.toString());
    }

    @Test
    void parseHttpUri() {
        BookmarkUri uri = BookmarkUri.parse("http://intranet/resource");
        assertEquals(ResourceScheme.HTTP, uri.scheme());
        assertNotNull(uri.toURI());
    }

    // ── Non-standard opaque schemes ──────────────────────────────────────────────

    @Test
    void parseNdvScheme() {
        BookmarkUri uri = BookmarkUri.parse("ndv://mainframe/system/program.cgp");
        assertEquals(ResourceScheme.NDV, uri.scheme());
        assertEquals("mainframe/system/program.cgp", uri.schemeSpecificPart());
        assertNull(uri.toURI()); // non-standard → no java.net.URI
    }

    @Test
    void parseMailScheme() {
        BookmarkUri uri = BookmarkUri.parse("mail://inbox/2024-03-01/subject");
        assertEquals(ResourceScheme.MAIL, uri.scheme());
        assertEquals("inbox/2024-03-01/subject", uri.schemeSpecificPart());
    }

    @Test
    void parseSharepointScheme() {
        BookmarkUri uri = BookmarkUri.parse("sharepoint://site/library/doc.docx");
        assertEquals(ResourceScheme.SHAREPOINT, uri.scheme());
        assertEquals("site/library/doc.docx", uri.schemeSpecificPart());
    }

    @Test
    void parseCustomScheme() {
        BookmarkUri uri = BookmarkUri.parse("mvs://SYS1.PROCLIB(MEMBER)");
        ResourceScheme scheme = uri.scheme();
        assertEquals("mvs", scheme.name());
        assertEquals("SYS1.PROCLIB(MEMBER)", uri.schemeSpecificPart());
    }

    // ── Factory method ───────────────────────────────────────────────────────────

    @Test
    void ofConstructsStandardUri() {
        BookmarkUri uri = BookmarkUri.of(ResourceScheme.FILE, "///C:/Projects/README.md");
        assertEquals(ResourceScheme.FILE, uri.scheme());
        assertEquals("file:///C:/Projects/README.md", uri.toString());
    }

    @Test
    void ofConstructsOpaqueUri() {
        BookmarkUri uri = BookmarkUri.of(ResourceScheme.NDV, "lib/object");
        assertEquals("ndv://lib/object", uri.toString());
    }

    // ── Round-trip ───────────────────────────────────────────────────────────────

    @Test
    void toStringRoundTripsStandardUri() {
        String original = "https://example.com/path?q=1";
        BookmarkUri uri = BookmarkUri.parse(original);
        assertEquals(original, uri.toString());
    }

    @Test
    void toStringRoundTripsOpaqueUri() {
        String original = "ndv://mainframe/path";
        BookmarkUri uri = BookmarkUri.parse(original);
        assertEquals(original, uri.toString());
    }

    // ── Equality ─────────────────────────────────────────────────────────────────

    @Test
    void equalityAndHashCode() {
        BookmarkUri a = BookmarkUri.parse("file:///path/file.txt");
        BookmarkUri b = BookmarkUri.parse("file:///path/file.txt");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentUrisNotEqual() {
        BookmarkUri a = BookmarkUri.parse("file:///path/a");
        BookmarkUri b = BookmarkUri.parse("file:///path/b");
        assertNotEquals(a, b);
    }

    // ── Error cases ──────────────────────────────────────────────────────────────

    @Test
    void parseNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> BookmarkUri.parse(null));
    }

    @Test
    void parseEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> BookmarkUri.parse(""));
    }

    @Test
    void parseMissingSchemeThrows() {
        assertThrows(IllegalArgumentException.class, () -> BookmarkUri.parse("noscheme"));
    }

    // ── ResourceScheme extensibility ─────────────────────────────────────────────

    @Test
    void resourceSchemePreservesCustomScheme() {
        ResourceScheme custom = ResourceScheme.of("tn3270");
        assertEquals("tn3270", custom.name());
        assertNotEquals(ResourceScheme.FILE, custom);
    }

    @Test
    void resourceSchemeWellKnownIdentity() {
        assertSame(ResourceScheme.FILE, ResourceScheme.of("file"));
        assertSame(ResourceScheme.FILE, ResourceScheme.of("FILE"));
        assertSame(ResourceScheme.NDV, ResourceScheme.of("ndv"));
    }
}
