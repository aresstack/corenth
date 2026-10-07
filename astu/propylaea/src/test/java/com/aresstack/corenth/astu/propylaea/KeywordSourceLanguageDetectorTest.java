package com.aresstack.corenth.astu.propylaea;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeywordSourceLanguageDetectorTest {

    private final KeywordSourceLanguageDetector detector = new KeywordSourceLanguageDetector();

    @Test
    void jcl_isDetectedByLeadingSlashes() {
        assertEquals(SourceLanguage.JCL, detector.detect("\n//MYJOB  JOB (ACCT),'X'\n//STEP1 EXEC PGM=IEFBR14\n"));
    }

    @Test
    void cobol_isDetectedByDivisionHeader() {
        assertEquals(SourceLanguage.COBOL, detector.detect("000100 IDENTIFICATION DIVISION.\n000200 PROGRAM-ID. ABC.\n"));
    }

    @Test
    void cobolMarkersInCommentLines_doNotCount() {
        assertEquals(SourceLanguage.UNKNOWN, detector.detect("000100* PROCEDURE DIVISION is mentioned only here\n"));
    }

    @Test
    void natural_isDetectedByNaturalOnlyStatements() {
        assertEquals(SourceLanguage.NATURAL, detector.detect("* header\nDEFINE DATA LOCAL\nEND-DEFINE\nEND\n"));
        assertEquals(SourceLanguage.NATURAL, detector.detect("callnat 'x'\n"));
    }

    @Test
    void plainTextAndEmptyText_areUnknown() {
        assertEquals(SourceLanguage.UNKNOWN, detector.detect("Dear reader,\nthis is prose.\n"));
        assertEquals(SourceLanguage.UNKNOWN, detector.detect(""));
        assertEquals(SourceLanguage.UNKNOWN, detector.detect("  \n "));
    }
}
