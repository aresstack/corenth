package com.aresstack.corenth.proasteion.emporion.holkas.mvs;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MvsLocationTest {

    @Test
    public void parseEmptyReturnsRoot() {
        assertEquals(MvsLocationType.ROOT, MvsLocation.parse("").type());
        assertEquals(MvsLocationType.ROOT, MvsLocation.parse(null).type());
    }

    @Test
    public void parseHlqReturnsHlq() {
        MvsLocation location = MvsLocation.parse("USERID");

        assertEquals(MvsLocationType.HLQ, location.type());
        assertEquals("'USERID'", location.logicalPath());
        assertEquals("USERID", location.displayName());
        assertEquals("'USERID.*'", location.queryPath());
    }

    @Test
    public void parseDatasetReturnsQualifierContext() {
        MvsLocation location = MvsLocation.parse("USERID.DATA.SET");

        assertEquals(MvsLocationType.QUALIFIER_CONTEXT, location.type());
        assertEquals("'USERID.DATA.SET'", location.logicalPath());
        assertEquals("SET", location.displayName());
        assertEquals("'USERID.DATA.SET.*'", location.queryPath());
    }

    @Test
    public void parseMemberReturnsMember() {
        MvsLocation location = MvsLocation.parse("USERID.PDS(MEMBER)");

        assertEquals(MvsLocationType.MEMBER, location.type());
        assertEquals("'USERID.PDS(MEMBER)'", location.logicalPath());
        assertEquals("MEMBER", location.displayName());
        assertFalse(location.isDirectory());
    }

    @Test
    public void datasetChildIsMember() {
        MvsLocation parent = MvsLocation.dataset("USERID.PDS");
        MvsLocation child = parent.createChild("MEMBER1");

        assertEquals(MvsLocationType.MEMBER, child.type());
        assertEquals("'USERID.PDS(MEMBER1)'", child.logicalPath());
        assertEquals("MEMBER1", child.displayName());
    }

    @Test
    public void wildcardQueryPreservesExistingWildcard() {
        assertEquals("'APAB*'", MvsLocation.hlq("APAB*").queryPath());
        assertEquals("'KKR07.ZABA*'", MvsLocation.qualifierContext("KKR07.ZABA*").queryPath());
        assertTrue(MvsQuoteNormalizer.hasWildcard("'KKR07.ZABA*'"));
        assertEquals("KKR07", MvsQuoteNormalizer.wildcardBase("KKR07.ZABA*"));
    }
}
