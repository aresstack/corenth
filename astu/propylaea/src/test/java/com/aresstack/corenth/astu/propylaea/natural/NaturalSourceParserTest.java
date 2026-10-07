package com.aresstack.corenth.astu.propylaea.natural;

import com.aresstack.corenth.astu.propylaea.ParsingFailureReason;
import com.aresstack.corenth.astu.propylaea.ParsingResult;
import com.aresstack.corenth.astu.propylaea.ProgramStructure;
import com.aresstack.corenth.astu.propylaea.SourceLanguage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.aresstack.corenth.astu.propylaea.StructureText.components;
import static com.aresstack.corenth.astu.propylaea.StructureText.diagnostics;
import static com.aresstack.corenth.astu.propylaea.StructureText.fixture;
import static com.aresstack.corenth.astu.propylaea.StructureText.relations;
import static com.aresstack.corenth.astu.propylaea.StructureText.request;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NaturalSourceParserTest {

    private final NaturalSourceParser parser = new NaturalSourceParser();

    private ProgramStructure parse(String unitName, String text) {
        ParsingResult result = parser.parse(request(unitName, SourceLanguage.NATURAL, text));
        assertTrue(result.isSuccess(), result.toString());
        return result.structure();
    }

    private static String lines(String... lines) {
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            text.append(line).append('\n');
        }
        return text.toString();
    }

    @Test
    void fixtureProgram_yieldsComponentsAndRelationsInSourceOrder() {
        ProgramStructure structure = parse("custupd", fixture(NaturalSourceParserTest.class, "CUSTUPD.NSP"));

        assertEquals(SourceLanguage.NATURAL, structure.language());
        assertEquals(Arrays.asList(
                "PROGRAM CUSTUPD L1-L41",
                "SUBROUTINE CHECK-BALANCE L35-L39"), components(structure));
        assertEquals(Arrays.asList(
                "L5 PROGRAM CUSTUPD INCLUDES EXTERNAL DATA_AREA CUSTLDA via LOCAL USING",
                "L6 PROGRAM CUSTUPD INCLUDES EXTERNAL DATA_AREA CUSTPDA via PARAMETER USING",
                "L16 PROGRAM CUSTUPD READS EXTERNAL DATA_STORE CUSTOMER-FILE via READ",
                "L17 PROGRAM CUSTUPD CALLS LOCAL SUBROUTINE CHECK-BALANCE via PERFORM",
                "L18 PROGRAM CUSTUPD WRITES EXTERNAL DATA_STORE CUSTOMER-FILE via UPDATE",
                "L20 PROGRAM CUSTUPD READS EXTERNAL DATA_STORE ORDER-FILE via FIND",
                "L21 PROGRAM CUSTUPD WRITES EXTERNAL DATA_STORE ORDER-FILE via DELETE",
                "L23 PROGRAM CUSTUPD WRITES EXTERNAL DATA_STORE CUSTOMER-FILE via STORE",
                "L24 PROGRAM CUSTUPD READS EXTERNAL DATA_STORE ORDER-FILE via GET",
                "L25 PROGRAM CUSTUPD READS EXTERNAL DATA_STORE CUSTOMER-FILE via HISTOGRAM",
                "L27 PROGRAM CUSTUPD CALLS EXTERNAL PROGRAM CUSTVAL via CALLNAT",
                "L28 PROGRAM CUSTUPD CALLS EXTERNAL PROGRAM CUSTMENU via FETCH RETURN",
                "L29 PROGRAM CUSTUPD CALLS DYNAMIC PROGRAM #PGM via CALLNAT",
                "L30 PROGRAM CUSTUPD CALLS EXTERNAL PROGRAM CEEDATE via CALL",
                "L31 PROGRAM CUSTUPD INCLUDES EXTERNAL COPYBOOK CUSTCC1 via INCLUDE",
                "L32 PROGRAM CUSTUPD CALLS EXTERNAL SUBROUTINE WRITE-LOG via PERFORM",
                "L37 SUBROUTINE CHECK-BALANCE CALLS EXTERNAL PROGRAM CUSTWARN via CALLNAT"), relations(structure));
        assertEquals(Collections.<String>emptyList(), diagnostics(structure));
    }

    @Test
    void parsingTwice_andWithCrLf_yieldsEqualStructures() {
        String text = fixture(NaturalSourceParserTest.class, "CUSTUPD.NSP");
        ProgramStructure first = parse("CUSTUPD", text);
        ProgramStructure second = parse("CUSTUPD", text);
        ProgramStructure crLf = parse("CUSTUPD", text.replace("\n", "\r\n"));

        assertEquals(first.components(), second.components());
        assertEquals(first.relations(), second.relations());
        assertEquals(first.relations(), crLf.relations());
        assertEquals(first.components(), crLf.components());
    }

    @Test
    void comments_areIgnoredInEveryForm() {
        ProgramStructure structure = parse("P", lines(
                "** CALLNAT 'C1'",
                "* CALLNAT 'C2'",
                "/* CALLNAT 'C3'",
                "    * CALLNAT 'C4'",
                "*",
                "CALLNAT 'REAL' /* CALLNAT 'C5'",
                "END"));

        assertEquals(Collections.singletonList("L6 PROGRAM P CALLS EXTERNAL PROGRAM REAL via CALLNAT"), relations(structure));
    }

    @Test
    void literals_hideKeywordsAndCommentMarkers() {
        ProgramStructure structure = parse("P", lines(
                "WRITE 'CALLNAT X' 'FETCH Y'",
                "CALLNAT 'A/*B'",
                "CALLNAT 'O''BRIEN'",
                "CALLNAT \"DQ\"",
                "MOVE 'INCLUDE X' TO #A",
                "END"));

        assertEquals(Arrays.asList(
                "L2 PROGRAM P CALLS EXTERNAL PROGRAM A/*B via CALLNAT",
                "L3 PROGRAM P CALLS EXTERNAL PROGRAM O'BRIEN via CALLNAT",
                "L4 PROGRAM P CALLS EXTERNAL PROGRAM DQ via CALLNAT"), relations(structure));
    }

    @Test
    void unterminatedLiteral_isDiagnosedAndDoesNotLeakIntoNextLine() {
        ProgramStructure structure = parse("P", lines(
                "WRITE 'OPEN",
                "CALLNAT 'NEXT'",
                "END"));

        assertEquals(Collections.singletonList("L2 PROGRAM P CALLS EXTERNAL PROGRAM NEXT via CALLNAT"), relations(structure));
        assertEquals(Collections.singletonList("UNTERMINATED_LITERAL L1"), diagnostics(structure));
    }

    @Test
    void variableOperands_areDynamicTargets() {
        ProgramStructure structure = parse("P", lines(
                "CALLNAT #SUBPGM",
                "FETCH PGM-NAME",
                "FETCH REPEAT 'MENU'",
                "CALL FILE 'ASMFILE'",
                "END"));

        assertEquals(Arrays.asList(
                "L1 PROGRAM P CALLS DYNAMIC PROGRAM #SUBPGM via CALLNAT",
                "L2 PROGRAM P CALLS DYNAMIC PROGRAM PGM-NAME via FETCH",
                "L3 PROGRAM P CALLS EXTERNAL PROGRAM MENU via FETCH REPEAT",
                "L4 PROGRAM P CALLS EXTERNAL PROGRAM ASMFILE via CALL FILE"), relations(structure));
    }

    @Test
    void missingOperands_areDiagnosedWithoutRelation() {
        ProgramStructure structure = parse("P", lines(
                "CALLNAT",
                "PERFORM",
                "INCLUDE",
                "READ",
                "PERFORM BREAK PROCESSING",
                "END"));

        assertEquals(Collections.<String>emptyList(), relations(structure));
        assertEquals(Arrays.asList(
                "UNSUPPORTED_CONSTRUCT L1",
                "UNSUPPORTED_CONSTRUCT L2",
                "UNSUPPORTED_CONSTRUCT L3",
                "UNSUPPORTED_CONSTRUCT L4"), diagnostics(structure));
    }

    @Test
    void labelsAndBranchKeywords_precedeRecognisedStatements() {
        ProgramStructure structure = parse("P", lines(
                "IF #A = 1",
                "THEN CALLNAT 'T1'",
                "ELSE CALLNAT 'E1'",
                "END-IF",
                "LBL. CALLNAT 'L1'",
                "END"));

        assertEquals(Arrays.asList(
                "L2 PROGRAM P CALLS EXTERNAL PROGRAM T1 via CALLNAT",
                "L3 PROGRAM P CALLS EXTERNAL PROGRAM E1 via CALLNAT",
                "L5 PROGRAM P CALLS EXTERNAL PROGRAM L1 via CALLNAT"), relations(structure));
    }

    @Test
    void undeclaredView_usesViewNameAndIsDiagnosed() {
        ProgramStructure structure = parse("P", lines(
                "DEFINE DATA LOCAL USING EMPLDA",
                "END-DEFINE",
                "READ EMPL-VIEW BY NAME",
                "END-READ",
                "END"));

        assertEquals(Arrays.asList(
                "L1 PROGRAM P INCLUDES EXTERNAL DATA_AREA EMPLDA via LOCAL USING",
                "L3 PROGRAM P READS EXTERNAL DATA_STORE EMPL-VIEW via READ"), relations(structure));
        assertEquals(Collections.singletonList("UNRESOLVED_REFERENCE L3"), diagnostics(structure));
    }

    @Test
    void readVariants_skipLimitsAndFillers_andWorkFilesAreNotDatabaseAccess() {
        ProgramStructure structure = parse("P", lines(
                "DEFINE DATA LOCAL",
                "01 EMP VIEW OF EMPLOYEES",
                "END-DEFINE",
                "READ (10) EMP BY NAME",
                "END-READ",
                "READ MULTI-FETCH OF 20 EMP PHYSICAL",
                "END-READ",
                "FIND FIRST EMP WITH NAME = 'X'",
                "FIND NUMBER EMP WITH NAME = 'X'",
                "FIND (5) RECORDS IN FILE EMP WITH NAME = 'X'",
                "END-FIND",
                "READ WORK FILE 1 #REC",
                "END-WORK",
                "GET TRANSACTION DATA #A",
                "GET SAME",
                "END"));

        assertEquals(Arrays.asList(
                "L4 PROGRAM P READS EXTERNAL DATA_STORE EMPLOYEES via READ",
                "L6 PROGRAM P READS EXTERNAL DATA_STORE EMPLOYEES via READ",
                "L8 PROGRAM P READS EXTERNAL DATA_STORE EMPLOYEES via FIND",
                "L9 PROGRAM P READS EXTERNAL DATA_STORE EMPLOYEES via FIND",
                "L10 PROGRAM P READS EXTERNAL DATA_STORE EMPLOYEES via FIND"), relations(structure));
        assertEquals(Collections.<String>emptyList(), diagnostics(structure));
    }

    @Test
    void updateAndDelete_bindToInnermostLoopOrLabel_andAreDiagnosedOtherwise() {
        ProgramStructure structure = parse("P", lines(
                "DEFINE DATA LOCAL",
                "1 EMP VIEW OF EMPLOYEES",
                "1 VEH VIEW OF VEHICLES",
                "END-DEFINE",
                "OUTER. READ EMP BY NAME",
                "  FIND VEH WITH PERSONNEL-ID = EMP.PERSONNEL-ID",
                "    UPDATE",
                "    DELETE (OUTER.)",
                "  END-FIND",
                "END-READ",
                "UPDATE",
                "DELETE (0100)",
                "END"));

        assertEquals(Arrays.asList(
                "L5 PROGRAM P READS EXTERNAL DATA_STORE EMPLOYEES via READ",
                "L6 PROGRAM P READS EXTERNAL DATA_STORE VEHICLES via FIND",
                "L7 PROGRAM P WRITES EXTERNAL DATA_STORE VEHICLES via UPDATE",
                "L8 PROGRAM P WRITES EXTERNAL DATA_STORE EMPLOYEES via DELETE"), relations(structure));
        assertEquals(Arrays.asList("UNRESOLVED_REFERENCE L11", "UNRESOLVED_REFERENCE L12"), diagnostics(structure));
    }

    @Test
    void unclosedAndUnmatchedBlocks_areDiagnosed() {
        ProgramStructure structure = parse("P", lines(
                "END-SUBROUTINE",
                "END-READ",
                "DEFINE SUBROUTINE OPEN-SUB",
                "  DEFINE SUBROUTINE INNER",
                "  READ EMP",
                "  CALLNAT 'INSIDE'",
                "DEFINE DATA LOCAL",
                "1 #A (A1)"));

        assertEquals(Arrays.asList(
                "PROGRAM P L1-L8",
                "SUBROUTINE OPEN-SUB L3-L8"), components(structure));
        assertEquals(Arrays.asList(
                "L5 SUBROUTINE OPEN-SUB READS EXTERNAL DATA_STORE EMP via READ",
                "L6 SUBROUTINE OPEN-SUB CALLS EXTERNAL PROGRAM INSIDE via CALLNAT"), relations(structure));
        assertEquals(Arrays.asList(
                "UNMATCHED_BLOCK_END L1",
                "UNMATCHED_BLOCK_END L2",
                "UNSUPPORTED_CONSTRUCT L4",
                "UNRESOLVED_REFERENCE L5",
                "UNTERMINATED_BLOCK L7",
                "UNTERMINATED_BLOCK L5",
                "UNTERMINATED_BLOCK L3"), diagnostics(structure));
    }

    @Test
    void duplicateSubroutine_keepsFirstDefinition() {
        ProgramStructure structure = parse("P", lines(
                "DEFINE SUBROUTINE S1",
                "END-SUBROUTINE",
                "DEFINE SUBROUTINE S1",
                "  CALLNAT 'X'",
                "END-SUBROUTINE",
                "PERFORM S1",
                "END"));

        assertEquals(Arrays.asList("PROGRAM P L1-L7", "SUBROUTINE S1 L1-L2"), components(structure));
        assertEquals(Arrays.asList(
                "L4 SUBROUTINE S1 CALLS EXTERNAL PROGRAM X via CALLNAT",
                "L6 PROGRAM P CALLS LOCAL SUBROUTINE S1 via PERFORM"), relations(structure));
        assertEquals(Collections.singletonList("UNSUPPORTED_CONSTRUCT L3"), diagnostics(structure));
    }

    @Test
    void subroutineWithSameNameAsUnit_isADistinctComponent() {
        ProgramStructure structure = parse("CALC", lines(
                "DEFINE SUBROUTINE CALC",
                "  CALLNAT 'X'",
                "END-SUBROUTINE",
                "END"));

        assertEquals(Collections.singletonList("L2 SUBROUTINE CALC CALLS EXTERNAL PROGRAM X via CALLNAT"), relations(structure));
    }

    @Test
    void lowerCaseSource_isNormalisedToUpperCaseNames() {
        ProgramStructure structure = parse("lower", lines(
                "define data local",
                "1 emp view of employees",
                "end-define",
                "callnat 'sub1'",
                "perform helper",
                "define subroutine helper",
                "  find emp with name = 'x'",
                "  end-find",
                "end-subroutine",
                "end"));

        assertEquals(Arrays.asList(
                "L4 PROGRAM LOWER CALLS EXTERNAL PROGRAM SUB1 via CALLNAT",
                "L5 PROGRAM LOWER CALLS LOCAL SUBROUTINE HELPER via PERFORM",
                "L7 SUBROUTINE HELPER READS EXTERNAL DATA_STORE EMPLOYEES via FIND"), relations(structure));
    }

    @Test
    void commentOnlySource_yieldsUnitWithoutRelations() {
        ProgramStructure structure = parse("P", lines("** nothing here", "* still nothing"));

        assertEquals(Collections.singletonList("PROGRAM P L1-L2"), components(structure));
        assertTrue(structure.relations().isEmpty());
        assertTrue(structure.diagnostics().isEmpty());
    }

    @Test
    void emptyOrBlankSource_isEmptySourceFailure() {
        for (String text : Arrays.asList("", "   \n\t\n")) {
            ParsingResult result = parser.parse(request("P", SourceLanguage.NATURAL, text));
            assertFalse(result.isSuccess());
            assertEquals(ParsingFailureReason.EMPTY_SOURCE, result.failureReason());
        }
    }

    @Test
    void binaryInput_isMalformedSourceFailure() {
        ParsingResult result = parser.parse(request("P", SourceLanguage.NATURAL, "CALLNAT 'X'\u0000\u0001"));

        assertFalse(result.isSuccess());
        assertEquals(ParsingFailureReason.MALFORMED_SOURCE, result.failureReason());
    }

    @Test
    void otherLanguage_isUnsupportedLanguageFailure() {
        ParsingResult result = parser.parse(request("P", SourceLanguage.COBOL, "CALLNAT 'X'"));

        assertFalse(result.isSuccess());
        assertEquals(ParsingFailureReason.UNSUPPORTED_LANGUAGE, result.failureReason());
        assertEquals(SourceLanguage.COBOL, result.language());
    }

    @Test
    void relationsList_isImmutable() {
        ProgramStructure structure = parse("P", lines("CALLNAT 'X'", "END"));
        List<?> relations = structure.relations();
        try {
            relations.clear();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("relations must be immutable");
    }
}
