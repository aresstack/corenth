package com.aresstack.corenth.astu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BookmarkUriTest {

    @Test
    void parseLocalScheme() {
        BookmarkUri uri = BookmarkUri.parse("local://documents/specification.pdf");
        assertEquals(ResourceScheme.LOCAL, uri.scheme());
        assertEquals("local", uri.rawScheme());
        assertEquals("documents/specification.pdf", uri.path());
    }

    @Test
    void parseNdvScheme() {
        BookmarkUri uri = BookmarkUri.parse("ndv://mainframe/system/program.cgp");
        assertEquals(ResourceScheme.NDV, uri.scheme());
        assertEquals("mainframe/system/program.cgp", uri.path());
    }

    @Test
    void parseMailScheme() {
        BookmarkUri uri = BookmarkUri.parse("mail://inbox/2024-03-01/subject");
        assertEquals(ResourceScheme.MAIL, uri.scheme());
        assertEquals("inbox/2024-03-01/subject", uri.path());
    }

    @Test
    void parseHttpsScheme() {
        BookmarkUri uri = BookmarkUri.parse("https://example.internal/wiki/page");
        assertEquals(ResourceScheme.HTTPS, uri.scheme());
        assertEquals("example.internal/wiki/page", uri.path());
    }

    @Test
    void parseUnknownSchemeResolvesToCustom() {
        BookmarkUri uri = BookmarkUri.parse("ftp://server/path");
        assertEquals(ResourceScheme.CUSTOM, uri.scheme());
        assertEquals("ftp", uri.rawScheme());
        assertEquals("server/path", uri.path());
    }

    @Test
    void ofConstructsValidUri() {
        BookmarkUri uri = BookmarkUri.of(ResourceScheme.SOURCE, "repo/main/File.java");
        assertEquals(ResourceScheme.SOURCE, uri.scheme());
        assertEquals("source://repo/main/File.java", uri.toString());
    }

    @Test
    void toStringRoundTrips() {
        String original = "local://documents/file.txt";
        BookmarkUri uri = BookmarkUri.parse(original);
        assertEquals(original, uri.toString());
    }

    @Test
    void equalityAndHashCode() {
        BookmarkUri a = BookmarkUri.parse("local://path/file");
        BookmarkUri b = BookmarkUri.parse("local://path/file");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentUrisNotEqual() {
        BookmarkUri a = BookmarkUri.parse("local://path/a");
        BookmarkUri b = BookmarkUri.parse("local://path/b");
        assertNotEquals(a, b);
    }

    @Test
    void parseNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> BookmarkUri.parse(null));
    }

    @Test
    void parseEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> BookmarkUri.parse(""));
    }

    @Test
    void parseMissingSeparatorThrows() {
        assertThrows(IllegalArgumentException.class, () -> BookmarkUri.parse("noscheme"));
    }

    @Test
    void schemeParseCaseInsensitive() {
        BookmarkUri uri = BookmarkUri.parse("LOCAL://docs/file");
        assertEquals(ResourceScheme.LOCAL, uri.scheme());
        assertEquals("LOCAL", uri.rawScheme());
    }
}
