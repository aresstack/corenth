package com.aresstack.corenth.astu.propylaea.natural;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Line-oriented structure and call extractor for Natural source in structured mode.
 *
 * <p>This is a relation extractor, not a compiler. It recognises statements that start a line
 * (after an optional statement label and an optional {@code THEN}/{@code ELSE}) and derives:
 * <ul>
 *   <li>components: the unit itself ({@link CodeComponentKind#PROGRAM}) and every inline
 *       {@code DEFINE SUBROUTINE ... END-SUBROUTINE} block;</li>
 *   <li>{@link RelationKind#CALLS}: {@code CALLNAT}, {@code FETCH [RETURN|REPEAT]},
 *       {@code CALL [FILE|LOOP]} and {@code PERFORM};</li>
 *   <li>{@link RelationKind#INCLUDES}: {@code INCLUDE} copycode and data areas referenced with
 *       {@code LOCAL|PARAMETER|GLOBAL USING};</li>
 *   <li>{@link RelationKind#READS}: {@code READ}, {@code FIND}, {@code HISTOGRAM}, {@code GET};</li>
 *   <li>{@link RelationKind#WRITES}: {@code STORE}, {@code UPDATE}, {@code DELETE}.</li>
 * </ul>
 * Database targets are the DDM named in the local {@code VIEW OF} definition; {@code UPDATE} and
 * {@code DELETE} are bound to the referenced statement label or the innermost open database loop.
 * Operands written as a constant are static targets; operands written as a variable are dynamic.
 * Everything else is ignored, and partially understood constructs produce diagnostics.
 */
public final class NaturalSourceParser implements SourceParser {

    private static final Pattern STATEMENT_LABEL = Pattern.compile("[A-Za-z0-9#@$_-]+\\.");
    private static final Pattern LEVEL_NUMBER = Pattern.compile("\\d{1,2}");

    @Override
    public SourceLanguage language() {
        return SourceLanguage.NATURAL;
    }

    @Override
    public ParsingResult parse(ParsingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.language() != SourceLanguage.NATURAL) {
            return ParsingResult.failure(request.source(), request.language(),
                    ParsingFailureReason.UNSUPPORTED_LANGUAGE, "Natural parser cannot parse " + request.language());
        }
        String text = request.text();
        if (text.trim().isEmpty()) {
            return ParsingResult.failure(request.source(), SourceLanguage.NATURAL,
                    ParsingFailureReason.EMPTY_SOURCE, "source text is empty");
        }
        if (text.indexOf('\u0000') >= 0) {
            return ParsingResult.failure(request.source(), SourceLanguage.NATURAL,
                    ParsingFailureReason.MALFORMED_SOURCE, "source text contains NUL characters and is not Natural source");
        }
        return ParsingResult.success(new Run(request).execute());
    }

    /** Splits text into lines, keeping line numbers aligned with the caller's view of the text. */
    static String[] lines(String text) {
        String[] lines = text.split("\r\n|\r|\n", -1);
        if (lines.length > 1 && lines[lines.length - 1].isEmpty()) {
            String[] trimmed = new String[lines.length - 1];
            System.arraycopy(lines, 0, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return lines;
    }

    /** One relation found before all subroutine blocks are known. */
    private static final class Draft {

        final RelationKind kind;
        final String fromSubroutine;
        final CodeComponentRef target;
        final String performTarget;
        final String statement;
        final int line;

        Draft(RelationKind kind, String fromSubroutine, CodeComponentRef target, String performTarget,
              String statement, int line) {
            this.kind = kind;
            this.fromSubroutine = fromSubroutine;
            this.target = target;
            this.performTarget = performTarget;
            this.statement = statement;
            this.line = line;
        }
    }

    /** An open READ, FIND or HISTOGRAM processing loop. */
    private static final class DatabaseLoop {

        final String keyword;
        final String view;
        final int line;

        DatabaseLoop(String keyword, String view, int line) {
            this.keyword = keyword;
            this.view = view;
            this.line = line;
        }
    }

    /** Mutable state of a single parse; never shared. */
    private static final class Run {

        private final ParsingRequest request;
        private final String[] lines;
        private final String unitName;
        private final List<Draft> drafts = new ArrayList<Draft>();
        private final List<SourceDiagnostic> diagnostics = new ArrayList<SourceDiagnostic>();
        private final Map<String, SourceLocation> subroutines = new LinkedHashMap<String, SourceLocation>();
        private final Map<String, String> viewToDdm = new HashMap<String, String>();
        private final Map<String, String> labelToView = new HashMap<String, String>();
        private final List<DatabaseLoop> loops = new ArrayList<DatabaseLoop>();

        private int line;
        private boolean inDefineData;
        private int defineDataLine;
        private String openSubroutine;
        private int openSubroutineLine;
        private boolean openSubroutineIsNew;
        private String label;

        Run(ParsingRequest request) {
            this.request = request;
            this.lines = lines(request.text());
            this.unitName = request.unitName().toUpperCase(Locale.ROOT);
        }

        ProgramStructure execute() {
            for (int i = 0; i < lines.length; i++) {
                line = i + 1;
                NaturalLineTokenizer.Line tokenized = NaturalLineTokenizer.tokenize(lines[i]);
                if (tokenized.unterminatedLiteral()) {
                    diagnose(SourceDiagnostic.Code.UNTERMINATED_LITERAL, line, "alphanumeric constant is not closed on this line");
                }
                if (!processLine(tokenized.tokens())) {
                    break;
                }
            }
            closeOpenBlocks();
            return buildStructure();
        }

        /** Returns {@code false} once the program-ending {@code END} statement is met. */
        private boolean processLine(List<NaturalToken> tokens) {
            int p = 0;
            label = null;
            while (p < tokens.size()) {
                NaturalToken token = tokens.get(p);
                if (token.isWord() && STATEMENT_LABEL.matcher(token.text()).matches() && !token.is(".")) {
                    label = normalizeLabel(token.text());
                    p++;
                } else if (token.is("THEN") || token.is("ELSE")) {
                    p++;
                } else {
                    break;
                }
            }
            if (p >= tokens.size() || !tokens.get(p).isWord()) {
                return true;
            }
            if (inDefineData) {
                processDataDefinition(tokens, p);
                return true;
            }
            String keyword = tokens.get(p).upper();
            if (keyword.equals("END") || keyword.equals(".")) {
                return false;
            }
            processStatement(keyword, tokens, p + 1);
            return true;
        }

        private void processDataDefinition(List<NaturalToken> tokens, int p) {
            NaturalToken first = tokens.get(p);
            if (first.is("END-DEFINE")) {
                inDefineData = false;
                return;
            }
            if (first.is("LOCAL") || first.is("PARAMETER") || first.is("GLOBAL") || first.is("INDEPENDENT")) {
                processDataScope(tokens, p);
                return;
            }
            if (LEVEL_NUMBER.matcher(first.text()).matches() && p + 3 < tokens.size()
                    && tokens.get(p + 1).isWord() && tokens.get(p + 2).is("VIEW")) {
                int q = p + 3;
                if (tokens.get(q).is("OF")) {
                    q++;
                }
                if (q < tokens.size() && tokens.get(q).isWord()) {
                    viewToDdm.put(tokens.get(p + 1).upper(), tokens.get(q).upper());
                } else {
                    diagnose(SourceDiagnostic.Code.UNSUPPORTED_CONSTRUCT, line, "VIEW definition without DDM name on the same line");
                }
            }
        }

        private void processDataScope(List<NaturalToken> tokens, int p) {
            String scope = tokens.get(p).upper();
            if (p + 1 < tokens.size() && tokens.get(p + 1).is("USING")) {
                NaturalToken area = p + 2 < tokens.size() ? tokens.get(p + 2) : null;
                if (area != null && area.isWord()) {
                    addDraft(RelationKind.INCLUDES,
                            CodeComponentRef.external(area.upper(), CodeComponentKind.DATA_AREA), scope + " USING");
                } else {
                    diagnose(SourceDiagnostic.Code.UNSUPPORTED_CONSTRUCT, line, scope + " USING without data area name");
                }
            }
        }

        private void processStatement(String keyword, List<NaturalToken> tokens, int q) {
            if (keyword.equals("DEFINE")) {
                processDefine(tokens, q);
            } else if (keyword.equals("END-SUBROUTINE")) {
                closeSubroutine();
            } else if (keyword.equals("PERFORM")) {
                processPerform(tokens, q);
            } else if (keyword.equals("CALLNAT")) {
                addProgramCall("CALLNAT", tokens, q);
            } else if (keyword.equals("FETCH")) {
                if (q < tokens.size() && (tokens.get(q).is("RETURN") || tokens.get(q).is("REPEAT"))) {
                    addProgramCall("FETCH " + tokens.get(q).upper(), tokens, q + 1);
                } else {
                    addProgramCall("FETCH", tokens, q);
                }
            } else if (keyword.equals("CALL")) {
                if (q < tokens.size() && (tokens.get(q).is("FILE") || tokens.get(q).is("LOOP"))) {
                    addProgramCall("CALL " + tokens.get(q).upper(), tokens, q + 1);
                } else {
                    addProgramCall("CALL", tokens, q);
                }
            } else if (keyword.equals("INCLUDE")) {
                processInclude(tokens, q);
            } else if (keyword.equals("READ")) {
                processRead(tokens, q);
            } else if (keyword.equals("FIND")) {
                processFind(tokens, q);
            } else if (keyword.equals("HISTOGRAM")) {
                processHistogram(tokens, q);
            } else if (keyword.equals("GET")) {
                processGet(tokens, q);
            } else if (keyword.equals("STORE")) {
                processStore(tokens, q);
            } else if (keyword.equals("UPDATE") || keyword.equals("DELETE")) {
                processRecordChange(keyword, tokens, q);
            } else if (keyword.equals("END-READ") || keyword.equals("END-FIND") || keyword.equals("END-HISTOGRAM")) {
                closeLoop(keyword.substring("END-".length()));
            }
        }

        private void processDefine(List<NaturalToken> tokens, int q) {
            if (q < tokens.size() && tokens.get(q).is("DATA")) {
                inDefineData = true;
                defineDataLine = line;
                if (q + 1 < tokens.size()) {
                    processDataDefinition(tokens, q + 1);
                }
                return;
            }
            if (q >= tokens.size() || !tokens.get(q).is("SUBROUTINE")) {
                return;
            }
            if (q + 1 >= tokens.size() || !tokens.get(q + 1).isWord()) {
                diagnose(SourceDiagnostic.Code.UNSUPPORTED_CONSTRUCT, line, "DEFINE SUBROUTINE without name on the same line");
                return;
            }
            String name = tokens.get(q + 1).upper();
            if (openSubroutine != null) {
                diagnose(SourceDiagnostic.Code.UNSUPPORTED_CONSTRUCT, line,
                        "nested DEFINE SUBROUTINE " + name + " inside " + openSubroutine + " is ignored");
                return;
            }
            openSubroutine = name;
            openSubroutineLine = line;
            openSubroutineIsNew = !subroutines.containsKey(name);
            if (openSubroutineIsNew) {
                subroutines.put(name, SourceLocation.line(line));
            } else {
                diagnose(SourceDiagnostic.Code.UNSUPPORTED_CONSTRUCT, line,
                        "subroutine " + name + " is defined more than once; the first definition is kept");
            }
        }

        private void closeSubroutine() {
            if (openSubroutine == null) {
                diagnose(SourceDiagnostic.Code.UNMATCHED_BLOCK_END, line, "END-SUBROUTINE without DEFINE SUBROUTINE");
                return;
            }
            if (openSubroutineIsNew) {
                subroutines.put(openSubroutine, new SourceLocation(openSubroutineLine, line));
            }
            openSubroutine = null;
        }

        private void processPerform(List<NaturalToken> tokens, int q) {
            if (q >= tokens.size() || !tokens.get(q).isWord()) {
                diagnose(SourceDiagnostic.Code.UNSUPPORTED_CONSTRUCT, line, "PERFORM without subroutine name on the same line");
                return;
            }
            if (tokens.get(q).is("BREAK")) {
                return;
            }
            drafts.add(new Draft(RelationKind.CALLS, openSubroutine, null, tokens.get(q).upper(), "PERFORM", line));
        }

        private void addProgramCall(String statement, List<NaturalToken> tokens, int q) {
            NaturalToken operand = q < tokens.size() ? tokens.get(q) : null;
            if (operand != null && operand.isLiteral() && !operand.text().trim().isEmpty()) {
                addDraft(RelationKind.CALLS, CodeComponentRef.external(
                        operand.text().trim().toUpperCase(Locale.ROOT), CodeComponentKind.PROGRAM), statement);
            } else if (operand != null && operand.isWord()) {
                addDraft(RelationKind.CALLS, CodeComponentRef.dynamic(operand.text(), CodeComponentKind.PROGRAM), statement);
            } else {
                diagnose(SourceDiagnostic.Code.UNSUPPORTED_CONSTRUCT, line,
                        statement + " without a program operand on the same line");
            }
        }

        private void processInclude(List<NaturalToken> tokens, int q) {
            NaturalToken operand = q < tokens.size() ? tokens.get(q) : null;
            if (operand != null && (operand.isWord() || operand.isLiteral()) && !operand.text().trim().isEmpty()) {
                addDraft(RelationKind.INCLUDES, CodeComponentRef.external(
                        operand.text().trim().toUpperCase(Locale.ROOT), CodeComponentKind.COPYBOOK), "INCLUDE");
            } else {
                diagnose(SourceDiagnostic.Code.UNSUPPORTED_CONSTRUCT, line, "INCLUDE without copycode name on the same line");
            }
        }

        private void processRead(List<NaturalToken> tokens, int q) {
            if (q < tokens.size() && tokens.get(q).is("WORK")) {
                return;
            }
            int v = skipLimitAndFillers(tokens, skipMultiFetch(tokens, q), "ALL", "RECORDS", "RECORD", "IN", "FILE");
            accessDatabase("READ", RelationKind.READS, tokens, v, true);
        }

        private void processFind(List<NaturalToken> tokens, int q) {
            boolean loop = true;
            int v = q;
            while (v < tokens.size() && (tokens.get(v).is("FIRST") || tokens.get(v).is("UNIQUE")
                    || tokens.get(v).is("NUMBER"))) {
                loop = false;
                v++;
            }
            v = skipLimitAndFillers(tokens, skipMultiFetch(tokens, v), "ALL", "RECORDS", "RECORD", "IN", "FILE");
            accessDatabase("FIND", RelationKind.READS, tokens, v, loop);
        }

        private void processHistogram(List<NaturalToken> tokens, int q) {
            int v = skipLimitAndFillers(tokens, q, "ALL", "VALUE", "VALUES", "IN", "FILE");
            accessDatabase("HISTOGRAM", RelationKind.READS, tokens, v, true);
        }

        private void processGet(List<NaturalToken> tokens, int q) {
            if (q < tokens.size() && (tokens.get(q).is("TRANSACTION") || tokens.get(q).is("SAME"))) {
                return;
            }
            int v = skipLimitAndFillers(tokens, q, "RECORD", "IN", "FILE");
            accessDatabase("GET", RelationKind.READS, tokens, v, false);
        }

        private void processStore(List<NaturalToken> tokens, int q) {
            int v = skipLimitAndFillers(tokens, q, "RECORD", "IN", "FILE");
            accessDatabase("STORE", RelationKind.WRITES, tokens, v, false);
        }

        private void processRecordChange(String keyword, List<NaturalToken> tokens, int q) {
            int r = q;
            while (r < tokens.size() && (tokens.get(r).is("RECORD") || tokens.get(r).is("IN")
                    || tokens.get(r).is("STATEMENT"))) {
                r++;
            }
            String view;
            if (r + 2 < tokens.size() && tokens.get(r).isSymbol('(') && tokens.get(r + 2).isSymbol(')')) {
                String referenced = normalizeLabel(tokens.get(r + 1).text());
                view = labelToView.get(referenced);
                if (view == null) {
                    diagnose(SourceDiagnostic.Code.UNRESOLVED_REFERENCE, line,
                            keyword + " references statement (" + referenced + ") whose view is unknown; no relation emitted");
                    return;
                }
            } else if (!loops.isEmpty()) {
                view = loops.get(loops.size() - 1).view;
            } else {
                diagnose(SourceDiagnostic.Code.UNRESOLVED_REFERENCE, line,
                        keyword + " outside an open READ/FIND/HISTOGRAM loop; no relation emitted");
                return;
            }
            addDraft(RelationKind.WRITES, CodeComponentRef.external(ddmFor(view), CodeComponentKind.DATA_STORE), keyword);
        }

        private void accessDatabase(String keyword, RelationKind kind, List<NaturalToken> tokens, int v, boolean opensLoop) {
            if (v >= tokens.size() || !tokens.get(v).isWord()) {
                diagnose(SourceDiagnostic.Code.UNSUPPORTED_CONSTRUCT, line, keyword + " without view name on the same line");
                return;
            }
            String view = tokens.get(v).upper();
            addDraft(kind, CodeComponentRef.external(ddmFor(view), CodeComponentKind.DATA_STORE), keyword);
            if (label != null) {
                labelToView.put(label, view);
            }
            if (opensLoop) {
                loops.add(new DatabaseLoop(keyword, view, line));
            }
        }

        private String ddmFor(String view) {
            String ddm = viewToDdm.get(view);
            if (ddm != null) {
                return ddm;
            }
            diagnose(SourceDiagnostic.Code.UNRESOLVED_REFERENCE, line,
                    "view " + view + " is not defined in this unit's DEFINE DATA; the view name is used as target");
            return view;
        }

        private void closeLoop(String keyword) {
            for (int i = loops.size() - 1; i >= 0; i--) {
                if (loops.get(i).keyword.equals(keyword)) {
                    if (i != loops.size() - 1) {
                        diagnose(SourceDiagnostic.Code.UNMATCHED_BLOCK_END, line,
                                "END-" + keyword + " closes inner loops that were not closed");
                    }
                    while (loops.size() > i) {
                        loops.remove(loops.size() - 1);
                    }
                    return;
                }
            }
            diagnose(SourceDiagnostic.Code.UNMATCHED_BLOCK_END, line, "END-" + keyword + " without open " + keyword);
        }

        private void closeOpenBlocks() {
            if (inDefineData) {
                diagnose(SourceDiagnostic.Code.UNTERMINATED_BLOCK, defineDataLine, "DEFINE DATA without END-DEFINE");
            }
            for (DatabaseLoop loop : loops) {
                diagnose(SourceDiagnostic.Code.UNTERMINATED_BLOCK, loop.line, loop.keyword + " loop without END-" + loop.keyword);
            }
            if (openSubroutine != null) {
                diagnose(SourceDiagnostic.Code.UNTERMINATED_BLOCK, openSubroutineLine,
                        "DEFINE SUBROUTINE " + openSubroutine + " without END-SUBROUTINE");
                if (openSubroutineIsNew) {
                    subroutines.put(openSubroutine, new SourceLocation(openSubroutineLine, lines.length));
                }
            }
        }

        private ProgramStructure buildStructure() {
            CodeComponent unit = new CodeComponent(unitName, CodeComponentKind.PROGRAM, new SourceLocation(1, lines.length));
            List<CodeComponent> components = new ArrayList<CodeComponent>();
            Map<String, CodeComponent> byName = new HashMap<String, CodeComponent>();
            components.add(unit);
            for (Map.Entry<String, SourceLocation> entry : subroutines.entrySet()) {
                CodeComponent subroutine = new CodeComponent(entry.getKey(), CodeComponentKind.SUBROUTINE, entry.getValue());
                components.add(subroutine);
                byName.put(entry.getKey(), subroutine);
            }
            List<CodeRelation> relations = new ArrayList<CodeRelation>();
            for (Draft draft : drafts) {
                CodeComponent from = draft.fromSubroutine == null ? unit : byName.get(draft.fromSubroutine);
                CodeComponentRef target = draft.target;
                if (target == null) {
                    target = byName.containsKey(draft.performTarget)
                            ? CodeComponentRef.local(draft.performTarget, CodeComponentKind.SUBROUTINE)
                            : CodeComponentRef.external(draft.performTarget, CodeComponentKind.SUBROUTINE);
                }
                relations.add(new CodeRelation(draft.kind, from, target, draft.statement, SourceLocation.line(draft.line)));
            }
            return new ProgramStructure(request.source(), SourceLanguage.NATURAL, components, relations, diagnostics);
        }

        private void addDraft(RelationKind kind, CodeComponentRef target, String statement) {
            drafts.add(new Draft(kind, openSubroutine, target, null, statement, line));
        }

        private void diagnose(SourceDiagnostic.Code code, int atLine, String message) {
            diagnostics.add(new SourceDiagnostic(code, SourceLocation.line(atLine), message));
        }

        private static int skipMultiFetch(List<NaturalToken> tokens, int q) {
            if (q < tokens.size() && tokens.get(q).is("MULTI-FETCH")) {
                q++;
                if (q < tokens.size() && (tokens.get(q).is("ON") || tokens.get(q).is("OFF"))) {
                    q++;
                } else if (q + 1 < tokens.size() && tokens.get(q).is("OF")) {
                    q += 2;
                }
            }
            return q;
        }

        private static int skipLimitAndFillers(List<NaturalToken> tokens, int q, String... fillers) {
            int v = q;
            boolean progressed = true;
            while (progressed && v < tokens.size()) {
                progressed = false;
                if (tokens.get(v).isSymbol('(')) {
                    int close = v + 1;
                    while (close < tokens.size() && !tokens.get(close).isSymbol(')')) {
                        close++;
                    }
                    v = Math.min(close + 1, tokens.size());
                    progressed = true;
                    continue;
                }
                for (String filler : fillers) {
                    if (tokens.get(v).is(filler)) {
                        v++;
                        progressed = true;
                        break;
                    }
                }
            }
            return v;
        }

        private static String normalizeLabel(String text) {
            String upper = text.toUpperCase(Locale.ROOT);
            return upper.endsWith(".") ? upper.substring(0, upper.length() - 1) : upper;
        }
    }
}
