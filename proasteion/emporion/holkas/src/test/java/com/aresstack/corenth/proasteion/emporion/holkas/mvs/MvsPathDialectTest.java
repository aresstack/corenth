package com.aresstack.corenth.proasteion.emporion.holkas.mvs;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MvsPathDialectTest {

    @Test
    public void mapsRootToQuotedEmptyPath() {
        MvsPathDialect dialect = new MvsPathDialect();

        assertEquals("''", dialect.toAbsolutePath("/"));
        assertEquals("''", dialect.toAbsolutePath(""));
        assertEquals("''", dialect.toAbsolutePath(null));
    }

    @Test
    public void quotesDatasetPaths() {
        MvsPathDialect dialect = new MvsPathDialect();

        assertEquals("'ABC.DEF'", dialect.toAbsolutePath("ABC.DEF"));
        assertEquals("'ABC.DEF'", dialect.toAbsolutePath("'ABC.DEF'"));
        assertEquals("'ABC.DEF'", dialect.toAbsolutePath("ABC/DEF"));
    }

    @Test
    public void detectsAndSplitsMemberPath() {
        MvsPathDialect dialect = new MvsPathDialect();

        assertTrue(dialect.isMemberPath("ABC.DEF(MEMBER)"));
        String[] parts = dialect.splitMember("'ABC.DEF(MEMBER)'");

        assertEquals("ABC.DEF", parts[0]);
        assertEquals("MEMBER", parts[1]);
    }

    @Test
    public void resolvesDotNotationAsMemberCandidateFirst() {
        MvsPathDialect dialect = new MvsPathDialect();

        List<String> candidates = dialect.resolveCandidates("ABC.DEF.MEMBER");

        assertEquals("'ABC.DEF(MEMBER)'", candidates.get(0));
        assertEquals("'ABC.DEF.MEMBER'", candidates.get(1));
    }

    @Test
    public void childOfBuildsDatasetMemberSpec() {
        MvsPathDialect dialect = new MvsPathDialect();

        assertEquals("'ABC.DEF(MEM)'", dialect.childOf("'ABC.DEF'", "MEM"));
    }
}
