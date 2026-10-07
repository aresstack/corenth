package com.aresstack.corenth.astu.propylaea.jcl;

import com.aresstack.corenth.astu.propylaea.CodeComponentKind;
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

class JclSourceParserTest {

    private final JclSourceParser parser = new JclSourceParser();

    private ProgramStructure parse(String unitName, String text) {
        ParsingResult result = parser.parse(request(unitName, SourceLanguage.JCL, text));
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
    void fixtureJob_yieldsStepsProceduresAndDependencies() {
        ProgramStructure structure = parse("payjob", fixture(JclSourceParserTest.class, "PAYJOB.JCL"));

        assertEquals(Arrays.asList(
                "JOB PAYJOB L1-L37",
                "PROCEDURE SORTPROC L9-L13",
                "STEP SORTPROC.SORT L10-L12",
                "STEP STEP010 L14-L19",
                "STEP STEP020 L23",
                "STEP STEP030 L24",
                "STEP STEP040 L25-L27",
                "STEP STEP050 L30-L31",
                "STEP STEP060 L32-L36"), components(structure));
        assertEquals(Arrays.asList(
                "L6 JOB PAYJOB READS EXTERNAL DATA_STORE PAY.LOADLIB via DD DISP=SHR",
                "L10 STEP SORTPROC.SORT CALLS EXTERNAL PROGRAM SORT via EXEC PGM",
                "L11 STEP SORTPROC.SORT READS DYNAMIC DATA_STORE &HLQ..INPUT via DD DISP=SHR",
                "L14 STEP STEP010 CALLS EXTERNAL PROGRAM PAYCALC via EXEC PGM",
                "L15 STEP STEP010 READS EXTERNAL DATA_STORE PAY.LOADLIB via DD DISP=SHR",
                "L16 STEP STEP010 WRITES EXTERNAL DATA_STORE PAY.MASTER.FILE via DD DISP=OLD",
                "L17 STEP STEP010 WRITES EXTERNAL DATA_STORE PAY.REPORT(+1) via DD DISP=NEW",
                "L23 STEP STEP020 CALLS LOCAL PROCEDURE SORTPROC via EXEC PROC",
                "L24 STEP STEP030 CALLS EXTERNAL PROCEDURE PAYPROC via EXEC PROC",
                "L25 STEP STEP040 CALLS EXTERNAL PROCEDURE NATBATCH via EXEC PROC",
                "L25 STEP STEP040 CALLS EXTERNAL PROGRAM PAYMAIN via NATURAL STACK LOGON",
                "L30 STEP STEP050 CALLS DYNAMIC PROGRAM &PGMNAME via EXEC PGM",
                "L31 STEP STEP050 INCLUDES EXTERNAL COPYBOOK PAYDDS via INCLUDE",
                "L32 STEP STEP060 CALLS EXTERNAL PROGRAM IEFBR14 via EXEC PGM",
                "L33 STEP STEP060 WRITES EXTERNAL DATA_STORE PAY.OLD.FILE via DD DISP=MOD"), relations(structure));
        assertEquals(Collections.<String>emptyList(), diagnostics(structure));
    }

    @Test
    void continuedStatement_spansAllItsLines() {
        ProgramStructure structure = parse("J", fixture(JclSourceParserTest.class, "PAYJOB.JCL"));

        assertEquals(17, structure.relations().get(6).location().startLine());
        assertEquals(18, structure.relations().get(6).location().endLine());
    }

    @Test
    void parsingTwice_andWithCrLf_yieldsEqualStructures() {
        String text = fixture(JclSourceParserTest.class, "PAYJOB.JCL");

        assertEquals(parse("J", text).relations(), parse("J", text).relations());
        assertEquals(parse("J", text).relations(), parse("J", text.replace("\n", "\r\n")).relations());
    }

    @Test
    void catalogedProcedure_isTheUnitAndKeepsUnqualifiedStepNames() {
        ProgramStructure structure = parse("PAYPROC", lines(
                "//PAYPROC PROC HLQ=PAY",
                "//CALC    EXEC PGM=PAYCALC",
                "//IN      DD DSN=&HLQ..IN,DISP=SHR"));

        assertEquals(CodeComponentKind.PROCEDURE, structure.unit().kind());
        assertEquals(Arrays.asList("PROCEDURE PAYPROC L1-L3", "STEP CALC L2-L3"), components(structure));
        assertEquals(Collections.<String>emptyList(), diagnostics(structure));
    }

    @Test
    void includeGroupMember_isACopybookUnit() {
        ProgramStructure structure = parse("PAYDDS", lines(
                "//* include group",
                "//PRINT   DD SYSOUT=*",
                "//AUDIT   DD DSN=PAY.AUDIT,DISP=MOD"));

        assertEquals(CodeComponentKind.COPYBOOK, structure.unit().kind());
        assertEquals(Collections.singletonList("L3 COPYBOOK PAYDDS WRITES EXTERNAL DATA_STORE PAY.AUDIT via DD DISP=MOD"),
                relations(structure));
    }

    @Test
    void commentsAndOperandComments_areIgnored() {
        ProgramStructure structure = parse("J", lines(
                "//J JOB 1",
                "//* EXEC PGM=NOPE1",
                "//S1 EXEC PGM=REAL1 PGM=NOPE2 IS A COMMENT",
                "//S2 EXEC PGM=REAL2,PARM='A B PGM=NOPE3' TRAILING"));

        assertEquals(Arrays.asList(
                "L3 STEP S1 CALLS EXTERNAL PROGRAM REAL1 via EXEC PGM",
                "L4 STEP S2 CALLS EXTERNAL PROGRAM REAL2 via EXEC PGM"), relations(structure));
    }

    @Test
    void apostropheStringOpenAtColumn71_continuesInColumn16() {
        String first = "//S1 EXEC PGM=NATBATCH,PARM='STACK=(LOGON PAYLIB;PAYMAIN)  ";
        StringBuilder padded = new StringBuilder(first);
        while (padded.length() < 71) {
            padded.append('X');
        }
        ProgramStructure structure = parse("J", lines(
                "//J JOB 1",
                padded.toString() + "X",
                "//             MORE'",
                "//DD1 DD DSN=PAY.AFTER,DISP=SHR"));

        assertEquals(Arrays.asList(
                "L2 STEP S1 CALLS EXTERNAL PROGRAM NATBATCH via EXEC PGM",
                "L2 STEP S1 CALLS EXTERNAL PROGRAM PAYMAIN via NATURAL STACK LOGON",
                "L4 STEP S1 READS EXTERNAL DATA_STORE PAY.AFTER via DD DISP=SHR"), relations(structure));
        assertEquals(3, structure.relations().get(0).location().endLine());
        assertEquals(Collections.<String>emptyList(), diagnostics(structure));
    }

    @Test
    void unnamedAndDuplicateSteps_getDeterministicNames() {
        ProgramStructure structure = parse("J", lines(
                "//J JOB 1",
                "//    EXEC PGM=A",
                "//S1  EXEC PGM=B",
                "//S1  EXEC PGM=C"));

        assertEquals(Arrays.asList("JOB J L1-L4", "STEP STEP@L2 L2", "STEP S1 L3", "STEP S1@L4 L4"), components(structure));
    }

    @Test
    void stepsWithSameNameInProcedureAndJob_stayDistinct() {
        ProgramStructure structure = parse("J", lines(
                "//J JOB 1",
                "//P1 PROC",
                "//S1 EXEC PGM=INPROC",
                "// PEND",
                "//S1 EXEC P1"));

        assertEquals(Arrays.asList("JOB J L1-L5", "PROCEDURE P1 L2-L4", "STEP P1.S1 L3", "STEP S1 L5"), components(structure));
        assertEquals("L5 STEP S1 CALLS LOCAL PROCEDURE P1 via EXEC PROC", relations(structure).get(1));
    }

    @Test
    void malformedStatements_areDiagnosed() {
        ProgramStructure structure = parse("J", lines(
                "//J JOB 1",
                "//NOOP",
                "//S1 EXEC COND=(4,LT)",
                "GARBAGE LINE",
                "// PEND",
                "//S2 EXEC PGM=X,",
                "//P2 PROC",
                "//S3 EXEC PGM=Y"));

        assertEquals(Arrays.asList(
                "UNSUPPORTED_CONSTRUCT L2",
                "UNSUPPORTED_CONSTRUCT L4",
                "UNSUPPORTED_CONSTRUCT L6",
                "UNSUPPORTED_CONSTRUCT L3",
                "UNMATCHED_BLOCK_END L5",
                "UNTERMINATED_BLOCK L7"), diagnostics(structure));
        assertEquals(Arrays.asList(
                "L6 STEP S2 CALLS EXTERNAL PROGRAM X via EXEC PGM",
                "L8 STEP P2.S3 CALLS EXTERNAL PROGRAM Y via EXEC PGM"), relations(structure));
    }

    @Test
    void inStreamDataWithStar_endsAtNextStatement() {
        ProgramStructure structure = parse("J", lines(
                "//J JOB 1",
                "//S1 EXEC PGM=A",
                "//IN DD *",
                "//S2 EXEC PGM=B"));

        assertEquals(2, structure.relations().size());
    }

    @Test
    void emptyBinaryAndForeignInput_areTypedFailures() {
        assertEquals(ParsingFailureReason.EMPTY_SOURCE,
                parser.parse(request("J", SourceLanguage.JCL, " \n")).failureReason());
        assertEquals(ParsingFailureReason.MALFORMED_SOURCE,
                parser.parse(request("J", SourceLanguage.JCL, "//J JOB\u0000")).failureReason());
        ParsingResult foreign = parser.parse(request("J", SourceLanguage.NATURAL, "//J JOB 1"));
        assertFalse(foreign.isSuccess());
        assertEquals(ParsingFailureReason.UNSUPPORTED_LANGUAGE, foreign.failureReason());
    }
}
