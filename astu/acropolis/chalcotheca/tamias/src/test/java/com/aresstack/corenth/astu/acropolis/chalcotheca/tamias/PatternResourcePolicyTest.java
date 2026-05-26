package com.aresstack.corenth.astu.acropolis.chalcotheca.tamias;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PatternResourcePolicyTest {

    @Test
    public void globMatches_escapesBackslashInPattern() {
        assertTrue(PatternResourcePolicy.globMatches("C:\\repo\\*.txt", "C:\\repo\\notes.txt"));
        assertFalse(PatternResourcePolicy.globMatches("C:\\repo\\*.txt", "C:\\repo\\notes.md"));
    }
}
