package com.aresstack.corenth.astu.propylaea.cobol;

import com.aresstack.corenth.astu.propylaea.ParsingFailureReason;
import com.aresstack.corenth.astu.propylaea.ParsingResult;
import com.aresstack.corenth.astu.propylaea.ProgramStructure;
import com.aresstack.corenth.astu.propylaea.SourceLanguage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.aresstack.corenth.astu.propylaea.StructureText.components;
import static com.aresstack.corenth.astu.propylaea.StructureText.diagnostics;
import static com.aresstack.corenth.astu.propylaea.StructureText.fixture;
import static com.aresstack.corenth.astu.propylaea.StructureText.relations;
import static com.aresstack.corenth.astu.propylaea.StructureText.request;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CobolSourceParserTest {

    private final CobolSourceParser parser = new CobolSourceParser();

    private ProgramStructure parse(String unitName, String text) {
        ParsingResult result = parser.parse(request(unitName, SourceLanguage.COBOL, text));
        assertTrue(result.isSuccess(), result.toString());
        return result.structure();
    }

    /** Builds reference-format cards: sequence area, indicator, then areas A and B from column 8. */
    private static String cards(String... areaText) {
        StringBuilder text = new StringBuilder();
        for (String line : areaText) {
            char indicator = ' ';
            String content = line;
            if (line.length() > 1 && line.charAt(1) == '|') {
                indicator = line.charAt(0);
                content = line.substring(2);
            }
            text.append("000000").append(indicator).append(content).append('\n');
        }
        return text.toString();
    }

    @Test
    void fixtureProgram_yieldsParagraphsCallsAndCopies() {
        ProgramStructure structure = parse("acctupd", fixture(CobolSourceParserTest.class, "ACCTUPD.cbl"));

        assertEquals(Arrays.asList(
                "PROGRAM ACCTUPD L1-L34",
                "SUBROUTINE MAIN L11-L34",
                "SUBROUTINE 0000-START L12-L28",
                "SUBROUTINE 1000-INIT L29-L32",
                "SUBROUTINE 1000-EXIT L33-L34"), components(structure));
        assertEquals(Arrays.asList(
                "L8 PROGRAM ACCTUPD INCLUDES EXTERNAL COPYBOOK ACCTREC via COPY",
                "L9 PROGRAM ACCTUPD INCLUDES EXTERNAL COPYBOOK SQLCA via EXEC SQL INCLUDE",
                "L13 SUBROUTINE 0000-START CALLS LOCAL SUBROUTINE 1000-INIT via PERFORM",
                "L13 SUBROUTINE 0000-START CALLS LOCAL SUBROUTINE 1000-EXIT via PERFORM THRU",
                "L15 SUBROUTINE 0000-START CALLS EXTERNAL PROGRAM LOGGER via CALL",
                "L20 SUBROUTINE 0000-START CALLS DYNAMIC PROGRAM WS-PGM via CALL",
                "L23 SUBROUTINE 0000-START CALLS EXTERNAL PROGRAM ACCTCHK via EXEC CICS LINK",
                "L26 SUBROUTINE 0000-START CALLS EXTERNAL PROGRAM ACCTPOST via CALL",
                "L32 SUBROUTINE 1000-INIT CALLS EXTERNAL PROGRAM AFTERLIT via CALL"), relations(structure));
        assertEquals(Collections.<String>emptyList(), diagnostics(structure));
    }

    @Test
    void parsingTwice_andWithCrLf_yieldsEqualStructures() {
        String text = fixture(CobolSourceParserTest.class, "ACCTUPD.cbl");

        assertEquals(parse("A", text).relations(), parse("A", text).relations());
        assertEquals(parse("A", text).relations(), parse("A", text.replace("\n", "\r\n")).relations());
        assertEquals(parse("A", text).components(), parse("A", text.replace("\n", "\r\n")).components());
    }

    @Test
    void sectionWithoutParagraphs_isTheCallOrigin() {
        ProgramStructure structure = parse("P", cards(
                "PROCEDURE DIVISION.",
                "A100 SECTION.",
                "    CALL 'SUB1'",
                "    PERFORM B200.",
                "B200 SECTION.",
                "    GOBACK."));

        assertEquals(Arrays.asList(
                "L3 SUBROUTINE A100 CALLS EXTERNAL PROGRAM SUB1 via CALL",
                "L4 SUBROUTINE A100 CALLS LOCAL SUBROUTINE B200 via PERFORM"), relations(structure));
        assertEquals(Arrays.asList("PROGRAM P L1-L6", "SUBROUTINE A100 L2-L4", "SUBROUTINE B200 L5-L6"),
                components(structure));
    }

    @Test
    void unknownPerformTarget_isDiagnosedWithoutRelation() {
        ProgramStructure structure = parse("P", cards(
                "PROCEDURE DIVISION.",
                "MAIN-PARA.",
                "    PERFORM MISSING-PARA",
                "    PERFORM WS-N TIMES",
                "        CONTINUE",
                "    END-PERFORM",
                "    PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > 3",
                "    END-PERFORM",
                "    PERFORM WITH TEST AFTER UNTIL WS-X = 1",
                "    END-PERFORM."));

        assertEquals(Collections.<String>emptyList(), relations(structure));
        assertEquals(Collections.singletonList("UNRESOLVED_REFERENCE L3"), diagnostics(structure));
    }

    @Test
    void literalsCommentsAndDebugLines_hideCalls() {
        ProgramStructure structure = parse("P", cards(
                "PROCEDURE DIVISION.",
                "MAIN-PARA.",
                "*|    CALL 'C1'",
                "/|    CALL 'C2'",
                "D|    CALL 'C3'",
                "    DISPLAY 'CALL C4' \"CALL C5\"",
                "    MOVE 'IT''S CALL C6' TO X *> CALL 'C7'",
                "    CALL \"REAL\"."));

        assertEquals(Collections.singletonList("L8 SUBROUTINE MAIN-PARA CALLS EXTERNAL PROGRAM REAL via CALL"),
                relations(structure));
    }

    @Test
    void copyVariants_andCopyInDataDivision_areIncludes() {
        ProgramStructure structure = parse("P", cards(
                "DATA DIVISION.",
                "WORKING-STORAGE SECTION.",
                "    COPY CPYA OF MYLIB.",
                "    COPY 'CPYB'.",
                "    COPY CPYC REPLACING ==:X:== BY ==Y==.",
                "PROCEDURE DIVISION.",
                "    COPY CPYD."));

        assertEquals(Arrays.asList(
                "L3 PROGRAM P INCLUDES EXTERNAL COPYBOOK CPYA via COPY",
                "L4 PROGRAM P INCLUDES EXTERNAL COPYBOOK CPYB via COPY",
                "L5 PROGRAM P INCLUDES EXTERNAL COPYBOOK CPYC via COPY",
                "L7 PROGRAM P INCLUDES EXTERNAL COPYBOOK CPYD via COPY"), relations(structure));
    }

    @Test
    void cicsTransfer_withVariableProgram_isDynamic_andXctlIsRecognised() {
        ProgramStructure structure = parse("P", cards(
                "PROCEDURE DIVISION.",
                "    EXEC CICS XCTL PROGRAM(WS-NEXT) END-EXEC",
                "    EXEC CICS LINK COMMAREA(X) END-EXEC",
                "    EXEC CICS RETURN END-EXEC."));

        assertEquals(Collections.singletonList("L2 PROGRAM P CALLS DYNAMIC PROGRAM WS-NEXT via EXEC CICS XCTL"),
                relations(structure));
        assertEquals(Collections.singletonList("UNSUPPORTED_CONSTRUCT L3"), diagnostics(structure));
    }

    @Test
    void unterminatedLiteralAndExec_areDiagnosed() {
        ProgramStructure structure = parse("P", cards(
                "PROCEDURE DIVISION.",
                "    DISPLAY 'OPEN",
                "    CALL 'NEXT'",
                "    EXEC SQL SELECT 1 INTO :X FROM T"));

        assertEquals(Collections.singletonList("L3 PROGRAM P CALLS EXTERNAL PROGRAM NEXT via CALL"), relations(structure));
        assertEquals(Arrays.asList("UNTERMINATED_LITERAL L2", "UNTERMINATED_BLOCK L4"), diagnostics(structure));
    }

    @Test
    void freeFormatDirective_stopsAnalysisWithDiagnostic() {
        ProgramStructure structure = parse("P", cards(
                "PROCEDURE DIVISION.",
                "    CALL 'BEFORE'",
                ">>SOURCE FORMAT FREE",
                "    CALL 'AFTER'"));

        assertEquals(Collections.singletonList("L2 PROGRAM P CALLS EXTERNAL PROGRAM BEFORE via CALL"), relations(structure));
        assertEquals(Collections.singletonList("UNSUPPORTED_CONSTRUCT L3"), diagnostics(structure));
    }

    @Test
    void duplicateParagraphNames_getDeterministicNames() {
        ProgramStructure structure = parse("P", cards(
                "PROCEDURE DIVISION.",
                "S1 SECTION.",
                "P1.",
                "    CONTINUE.",
                "S2 SECTION.",
                "P1.",
                "    CALL 'X'."));

        assertEquals(Arrays.asList("PROGRAM P L1-L7", "SUBROUTINE S1 L2-L4", "SUBROUTINE P1 L3-L4",
                "SUBROUTINE S2 L5-L7", "SUBROUTINE P1@L6 L6-L7"), components(structure));
        assertEquals(Collections.singletonList("L7 SUBROUTINE P1@L6 CALLS EXTERNAL PROGRAM X via CALL"), relations(structure));
    }

    @Test
    void emptyBinaryAndForeignInput_areTypedFailures() {
        assertEquals(ParsingFailureReason.EMPTY_SOURCE,
                parser.parse(request("P", SourceLanguage.COBOL, "")).failureReason());
        assertEquals(ParsingFailureReason.MALFORMED_SOURCE,
                parser.parse(request("P", SourceLanguage.COBOL, "000100 CALL 'X'\u0000")).failureReason());
        ParsingResult foreign = parser.parse(request("P", SourceLanguage.JCL, "000100 CALL 'X'"));
        assertFalse(foreign.isSuccess());
        assertEquals(ParsingFailureReason.UNSUPPORTED_LANGUAGE, foreign.failureReason());
    }
}
