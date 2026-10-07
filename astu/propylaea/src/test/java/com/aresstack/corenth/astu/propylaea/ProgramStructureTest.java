package com.aresstack.corenth.astu.propylaea;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.aresstack.corenth.astu.propylaea.StructureText.ref;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProgramStructureTest {

    private static final CodeComponent UNIT = new CodeComponent("P", CodeComponentKind.PROGRAM, new SourceLocation(1, 10));
    private static final CodeComponent SUB = new CodeComponent("S", CodeComponentKind.SUBROUTINE, new SourceLocation(5, 8));

    private static CodeRelation call(CodeComponent from, CodeComponentRef target) {
        return new CodeRelation(RelationKind.CALLS, from, target, "PERFORM", SourceLocation.line(3));
    }

    private static ProgramStructure structure(List<CodeComponent> components, List<CodeRelation> relations) {
        return new ProgramStructure(ref("P"), SourceLanguage.NATURAL, components, relations,
                Collections.<SourceDiagnostic>emptyList());
    }

    @Test
    void sourceLocation_rejectsInvalidRanges() {
        assertThrows(IllegalArgumentException.class, () -> new SourceLocation(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SourceLocation(3, 2));
        assertEquals(new SourceLocation(4, 4), SourceLocation.line(4));
    }

    @Test
    void unit_isFirstComponent() {
        ProgramStructure structure = structure(Arrays.asList(UNIT, SUB), Collections.<CodeRelation>emptyList());

        assertSame(UNIT, structure.unit());
        assertEquals(SUB, structure.component("S", CodeComponentKind.SUBROUTINE));
    }

    @Test
    void sameNameWithDifferentKind_isAllowed_butDuplicateNameAndKindIsRejected() {
        CodeComponent sameNameSub = new CodeComponent("P", CodeComponentKind.SUBROUTINE, SourceLocation.line(2));
        structure(Arrays.asList(UNIT, sameNameSub), Collections.<CodeRelation>emptyList());

        CodeComponent duplicate = new CodeComponent("S", CodeComponentKind.SUBROUTINE, SourceLocation.line(9));
        assertThrows(IllegalArgumentException.class,
                () -> structure(Arrays.asList(UNIT, SUB, duplicate), Collections.<CodeRelation>emptyList()));
    }

    @Test
    void relationFromForeignComponent_isRejected() {
        CodeComponent foreign = new CodeComponent("X", CodeComponentKind.PROGRAM, SourceLocation.line(1));

        assertThrows(IllegalArgumentException.class, () -> structure(Collections.singletonList(UNIT),
                Collections.singletonList(call(foreign, CodeComponentRef.external("Y", CodeComponentKind.PROGRAM)))));
    }

    @Test
    void localTargetMustExistWithExpectedKind() {
        structure(Arrays.asList(UNIT, SUB),
                Collections.singletonList(call(UNIT, CodeComponentRef.local("S", CodeComponentKind.SUBROUTINE))));

        assertThrows(IllegalArgumentException.class, () -> structure(Arrays.asList(UNIT, SUB),
                Collections.singletonList(call(UNIT, CodeComponentRef.local("MISSING", CodeComponentKind.SUBROUTINE)))));
        assertThrows(IllegalArgumentException.class, () -> structure(Arrays.asList(UNIT, SUB),
                Collections.singletonList(call(UNIT, CodeComponentRef.local("S", CodeComponentKind.PROGRAM)))));
    }

    @Test
    void unknownLanguageAndEmptyComponents_areRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ProgramStructure(ref("P"), SourceLanguage.UNKNOWN,
                Collections.singletonList(UNIT), Collections.<CodeRelation>emptyList(), Collections.<SourceDiagnostic>emptyList()));
        assertThrows(IllegalArgumentException.class, () -> structure(Collections.<CodeComponent>emptyList(),
                Collections.<CodeRelation>emptyList()));
    }

    @Test
    void relationsByKind_keepSourceOrder() {
        CodeRelation first = call(UNIT, CodeComponentRef.local("S", CodeComponentKind.SUBROUTINE));
        CodeRelation include = new CodeRelation(RelationKind.INCLUDES, UNIT,
                CodeComponentRef.external("CC", CodeComponentKind.COPYBOOK), "INCLUDE", SourceLocation.line(4));
        CodeRelation second = new CodeRelation(RelationKind.CALLS, SUB,
                CodeComponentRef.dynamic("#P", CodeComponentKind.PROGRAM), "CALLNAT", SourceLocation.line(6));
        ProgramStructure structure = structure(Arrays.asList(UNIT, SUB), Arrays.asList(first, include, second));

        assertEquals(Arrays.asList(first, second), structure.relations(RelationKind.CALLS));
        assertEquals(Collections.singletonList(include), structure.relations(RelationKind.INCLUDES));
    }

    @Test
    void inputListsAreCopied() {
        List<CodeComponent> components = new java.util.ArrayList<CodeComponent>(Arrays.asList(UNIT));
        ProgramStructure structure = structure(components, Collections.<CodeRelation>emptyList());
        components.add(SUB);

        assertEquals(1, structure.components().size());
        assertThrows(UnsupportedOperationException.class, () -> structure.components().add(SUB));
    }

    @Test
    void parsingRequest_rejectsMissingParts_andTrimsUnitName() {
        assertThrows(IllegalArgumentException.class, () -> new ParsingRequest(null, "P", SourceLanguage.NATURAL, ""));
        assertThrows(IllegalArgumentException.class, () -> new ParsingRequest(ref("P"), " ", SourceLanguage.NATURAL, ""));
        assertThrows(IllegalArgumentException.class, () -> new ParsingRequest(ref("P"), "P", null, ""));
        assertThrows(IllegalArgumentException.class, () -> new ParsingRequest(ref("P"), "P", SourceLanguage.NATURAL, null));
        assertEquals("P", new ParsingRequest(ref("P"), " P ", SourceLanguage.NATURAL, "").unitName());
    }
}
