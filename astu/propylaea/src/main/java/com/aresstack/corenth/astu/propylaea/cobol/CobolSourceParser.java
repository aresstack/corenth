package com.aresstack.corenth.astu.propylaea.cobol;

import com.aresstack.corenth.astu.propylaea.CodeComponent;
import com.aresstack.corenth.astu.propylaea.CodeComponentKind;
import com.aresstack.corenth.astu.propylaea.CodeComponentRef;
import com.aresstack.corenth.astu.propylaea.CodeRelation;
import com.aresstack.corenth.astu.propylaea.ParsingFailureReason;
import com.aresstack.corenth.astu.propylaea.ParsingRequest;
import com.aresstack.corenth.astu.propylaea.ParsingResult;
import com.aresstack.corenth.astu.propylaea.ProgramStructure;
import com.aresstack.corenth.astu.propylaea.RelationKind;
import com.aresstack.corenth.astu.propylaea.SourceDiagnostic;
import com.aresstack.corenth.astu.propylaea.SourceLanguage;
import com.aresstack.corenth.astu.propylaea.SourceLocation;
import com.aresstack.corenth.astu.propylaea.SourceParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Structure and call extractor for COBOL programs in fixed reference format.
 *
 * <p>Components: the unit ({@link CodeComponentKind#PROGRAM}) and every section and paragraph of
 * the procedure division ({@link CodeComponentKind#SUBROUTINE}); a header is a name starting in
 * area A (columns 8-11) followed by a separator period or by {@code SECTION}. Relations:
 * <ul>
 *   <li>{@link RelationKind#CALLS}: {@code CALL} (literal: external, identifier: dynamic),
 *       {@code PERFORM name [THRU name]} to local sections/paragraphs, and
 *       {@code EXEC CICS LINK|XCTL PROGRAM(...)};</li>
 *   <li>{@link RelationKind#INCLUDES}: {@code COPY} anywhere and {@code EXEC SQL INCLUDE}.</li>
 * </ul>
 * Inline {@code PERFORM} forms, {@code GO TO}, file I/O verbs and embedded SQL table access are
 * not modelled. Nested programs are attributed to the unit. Free format is rejected with a
 * diagnostic.
 */
public final class CobolSourceParser implements SourceParser {

    private static final int AREA_A_FIRST_COLUMN = 8;
    private static final int AREA_A_LAST_COLUMN = 11;

    private static final Set<String> NON_HEADER_WORDS = new HashSet<String>(Arrays.asList(
            "DECLARATIVES", "END", "EXIT", "GOBACK", "STOP", "CONTINUE", "COPY", "EJECT", "SKIP1", "SKIP2",
            "SKIP3", "REPLACE"));

    private static final Set<String> INLINE_PERFORM_WORDS = new HashSet<String>(Arrays.asList(
            "UNTIL", "VARYING", "WITH", "TEST", "FOREVER"));

    @Override
    public SourceLanguage language() {
        return SourceLanguage.COBOL;
    }

    @Override
    public ParsingResult parse(ParsingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.language() != SourceLanguage.COBOL) {
            return ParsingResult.failure(request.source(), request.language(),
                    ParsingFailureReason.UNSUPPORTED_LANGUAGE, "COBOL parser cannot parse " + request.language());
        }
        String text = request.text();
        if (text.trim().isEmpty()) {
            return ParsingResult.failure(request.source(), SourceLanguage.COBOL,
                    ParsingFailureReason.EMPTY_SOURCE, "source text is empty");
        }
        if (text.indexOf('\u0000') >= 0) {
            return ParsingResult.failure(request.source(), SourceLanguage.COBOL,
                    ParsingFailureReason.MALFORMED_SOURCE, "source text contains NUL characters and is not COBOL");
        }
        return ParsingResult.success(new Run(request).execute());
    }

    private static String[] lines(String text) {
        String[] lines = text.split("\r\n|\r|\n", -1);
        if (lines.length > 1 && lines[lines.length - 1].isEmpty()) {
            String[] trimmed = new String[lines.length - 1];
            System.arraycopy(lines, 0, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return lines;
    }

    /** A section or paragraph whose end is known only after the next header. */
    private static final class Block {

        final String name;
        final boolean section;
        final int startLine;
        int endLine;
        CodeComponent built;

        Block(String name, boolean section, int startLine) {
            this.name = name;
            this.section = section;
            this.startLine = startLine;
            this.endLine = startLine;
        }

        CodeComponent component() {
            if (built == null) {
                built = new CodeComponent(name, CodeComponentKind.SUBROUTINE, new SourceLocation(startLine, endLine));
            }
            return built;
        }
    }

    /** One relation whose origin and, for PERFORM, target are bound at the end. */
    private static final class Draft {

        final RelationKind kind;
        final Block from;
        final CodeComponentRef target;
        final String performTarget;
        final String statement;
        final int line;

        Draft(RelationKind kind, Block from, CodeComponentRef target, String performTarget, String statement, int line) {
            this.kind = kind;
            this.from = from;
            this.target = target;
            this.performTarget = performTarget;
            this.statement = statement;
            this.line = line;
        }
    }

    /** Mutable state of a single parse; never shared. */
    private static final class Run {

        private final ParsingRequest request;
        private final String[] lines;
        private final List<SourceDiagnostic> diagnostics = new ArrayList<SourceDiagnostic>();
        private final List<Block> blocks = new ArrayList<Block>();
        private final Map<String, Block> blocksByName = new HashMap<String, Block>();
        private final List<Draft> drafts = new ArrayList<Draft>();

        private List<CobolToken> tokens;
        private boolean inProcedureDivision;
        private Block currentSection;
        private Block currentParagraph;

        Run(ParsingRequest request) {
            this.request = request;
            this.lines = lines(request.text());
        }

        ProgramStructure execute() {
            tokens = new CobolTokenizer(lines, diagnostics).tokenize();
            int i = 0;
            while (i < tokens.size()) {
                i = step(i);
            }
            closeBlocks();
            return build();
        }

        /** Processes the token at index i and returns the index of the next unprocessed token. */
        private int step(int i) {
            CobolToken token = tokens.get(i);
            if (token.is("PROCEDURE") && wordAt(i + 1, "DIVISION")) {
                inProcedureDivision = true;
                return i + 2;
            }
            if (token.is("COPY")) {
                return copyStatement(i);
            }
            if (token.is("EXEC") || token.is("EXECUTE")) {
                return execBlock(i);
            }
            if (!inProcedureDivision) {
                return i + 1;
            }
            if (isHeader(i)) {
                return header(i);
            }
            if (token.is("CALL")) {
                return callStatement(i);
            }
            if (token.is("PERFORM")) {
                return performStatement(i);
            }
            return i + 1;
        }

        private boolean isHeader(int i) {
            CobolToken token = tokens.get(i);
            if (!token.isWord() || token.column() < AREA_A_FIRST_COLUMN || token.column() > AREA_A_LAST_COLUMN
                    || NON_HEADER_WORDS.contains(token.upper())) {
                return false;
            }
            if (i + 1 < tokens.size() && tokens.get(i + 1).isPeriod()) {
                return true;
            }
            return wordAt(i + 1, "SECTION");
        }

        private int header(int i) {
            CobolToken token = tokens.get(i);
            boolean section = wordAt(i + 1, "SECTION");
            Block block = register(new Block(token.upper(), section, token.line()));
            closeCurrent(section, token.line() - 1);
            if (section) {
                currentSection = block;
                currentParagraph = null;
            } else {
                currentParagraph = block;
            }
            return section ? i + 2 : i + 1;
        }

        private void closeCurrent(boolean section, int endLine) {
            if (currentParagraph != null) {
                currentParagraph.endLine = Math.max(currentParagraph.startLine, endLine);
            }
            if (section && currentSection != null) {
                currentSection.endLine = Math.max(currentSection.startLine, endLine);
            }
        }

        private void closeBlocks() {
            closeCurrent(true, lines.length);
        }

        private int copyStatement(int i) {
            CobolToken operand = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
            if (operand != null && (operand.isWord() || operand.isLiteral()) && !operand.text().trim().isEmpty()) {
                add(RelationKind.INCLUDES, CodeComponentRef.external(
                        operand.text().trim().toUpperCase(Locale.ROOT), CodeComponentKind.COPYBOOK), "COPY", tokens.get(i).line());
                return i + 2;
            }
            diagnose(SourceDiagnostic.Code.UNSUPPORTED_CONSTRUCT, tokens.get(i).line(), "COPY without copybook name");
            return i + 1;
        }

        private int callStatement(int i) {
            CobolToken operand = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
            int line = tokens.get(i).line();
            if (operand != null && operand.isLiteral() && !operand.text().trim().isEmpty()) {
                add(RelationKind.CALLS, CodeComponentRef.external(
                        operand.text().trim().toUpperCase(Locale.ROOT), CodeComponentKind.PROGRAM), "CALL", line);
                return i + 2;
            }
            if (operand != null && operand.isWord()) {
                add(RelationKind.CALLS, CodeComponentRef.dynamic(operand.text(), CodeComponentKind.PROGRAM), "CALL", line);
                return i + 2;
            }
            diagnose(SourceDiagnostic.Code.UNSUPPORTED_CONSTRUCT, line, "CALL without program operand");
            return i + 1;
        }

        private int performStatement(int i) {
            CobolToken target = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
            if (target == null || !target.isWord() || INLINE_PERFORM_WORDS.contains(target.upper())
                    || wordAt(i + 2, "TIMES")) {
                return i + 1;
            }
            int line = tokens.get(i).line();
            drafts.add(new Draft(RelationKind.CALLS, origin(), null, target.upper(), "PERFORM", line));
            if ((wordAt(i + 2, "THRU") || wordAt(i + 2, "THROUGH")) && i + 3 < tokens.size() && tokens.get(i + 3).isWord()) {
                drafts.add(new Draft(RelationKind.CALLS, origin(), null, tokens.get(i + 3).upper(), "PERFORM THRU", line));
                return i + 4;
            }
            return i + 2;
        }

        private int execBlock(int i) {
            int end = i + 1;
            while (end < tokens.size() && !tokens.get(end).is("END-EXEC")) {
                end++;
            }
            if (end >= tokens.size()) {
                diagnose(SourceDiagnostic.Code.UNTERMINATED_BLOCK, tokens.get(i).line(), "EXEC without END-EXEC");
            }
            int line = tokens.get(i).line();
            if (wordAt(i + 1, "SQL") && wordAt(i + 2, "INCLUDE") && i + 3 < end) {
                CobolToken member = tokens.get(i + 3);
                add(RelationKind.INCLUDES, CodeComponentRef.external(
                        member.text().trim().toUpperCase(Locale.ROOT), CodeComponentKind.COPYBOOK), "EXEC SQL INCLUDE", line);
            } else if (wordAt(i + 1, "CICS") && (wordAt(i + 2, "LINK") || wordAt(i + 2, "XCTL"))) {
                cicsTransfer(i, end, "EXEC CICS " + tokens.get(i + 2).upper(), line);
            }
            return Math.min(end + 1, tokens.size());
        }

        private void cicsTransfer(int i, int end, String statement, int line) {
            for (int p = i + 3; p + 2 < end; p++) {
                if (tokens.get(p).is("PROGRAM") && tokens.get(p + 1).isSymbol('(')) {
                    CobolToken operand = tokens.get(p + 2);
                    if (operand.isLiteral() && !operand.text().trim().isEmpty()) {
                        add(RelationKind.CALLS, CodeComponentRef.external(
                                operand.text().trim().toUpperCase(Locale.ROOT), CodeComponentKind.PROGRAM), statement, line);
                    } else {
                        add(RelationKind.CALLS, CodeComponentRef.dynamic(operand.text(), CodeComponentKind.PROGRAM), statement, line);
                    }
                    return;
                }
            }
            diagnose(SourceDiagnostic.Code.UNSUPPORTED_CONSTRUCT, line, statement + " without PROGRAM option");
        }

        private Block origin() {
            return currentParagraph != null ? currentParagraph : currentSection;
        }

        private void add(RelationKind kind, CodeComponentRef target, String statement, int line) {
            drafts.add(new Draft(kind, inProcedureDivision ? origin() : null, target, null, statement, line));
        }

        private Block register(Block block) {
            Block result = block;
            if (blocksByName.containsKey(block.name)) {
                result = new Block(block.name + "@L" + block.startLine, block.section, block.startLine);
            } else {
                blocksByName.put(block.name, block);
            }
            blocks.add(result);
            return result;
        }

        private ProgramStructure build() {
            CodeComponent unit = new CodeComponent(request.unitName().toUpperCase(Locale.ROOT), CodeComponentKind.PROGRAM,
                    new SourceLocation(1, lines.length));
            List<CodeComponent> components = new ArrayList<CodeComponent>();
            components.add(unit);
            for (Block block : blocks) {
                components.add(block.component());
            }
            List<CodeRelation> relations = new ArrayList<CodeRelation>();
            for (Draft draft : drafts) {
                CodeComponentRef target = draft.target;
                if (target == null) {
                    if (!blocksByName.containsKey(draft.performTarget)) {
                        diagnose(SourceDiagnostic.Code.UNRESOLVED_REFERENCE, draft.line,
                                draft.statement + " " + draft.performTarget + " names no section or paragraph of this unit");
                        continue;
                    }
                    target = CodeComponentRef.local(draft.performTarget, CodeComponentKind.SUBROUTINE);
                }
                CodeComponent from = draft.from == null ? unit : draft.from.component();
                relations.add(new CodeRelation(draft.kind, from, target, draft.statement, SourceLocation.line(draft.line)));
            }
            return new ProgramStructure(request.source(), SourceLanguage.COBOL, components, relations, diagnostics);
        }

        private boolean wordAt(int index, String keyword) {
            return index < tokens.size() && tokens.get(index).is(keyword);
        }

        private void diagnose(SourceDiagnostic.Code code, int line, String message) {
            diagnostics.add(new SourceDiagnostic(code, SourceLocation.line(line), message));
        }
    }
}
