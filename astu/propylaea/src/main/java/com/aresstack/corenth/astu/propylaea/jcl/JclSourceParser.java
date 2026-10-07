package com.aresstack.corenth.astu.propylaea.jcl;

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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structure and dependency extractor for z/OS JCL members: jobs, cataloged procedures and
 * include groups.
 *
 * <p>Components: the member itself (kind {@link CodeComponentKind#JOB} if it contains a
 * {@code JOB} statement, {@link CodeComponentKind#PROCEDURE} if it starts with {@code PROC},
 * otherwise {@link CodeComponentKind#COPYBOOK}), further jobs, in-stream procedures and every
 * {@code EXEC} step. Relations:
 * <ul>
 *   <li>{@link RelationKind#CALLS}: {@code EXEC PGM=}, {@code EXEC PROC=} or {@code EXEC name}, and a
 *       Natural program started through {@code STACK=(LOGON library;program)} in the step operands;</li>
 *   <li>{@link RelationKind#INCLUDES}: {@code INCLUDE MEMBER=};</li>
 *   <li>{@link RelationKind#READS}/{@link RelationKind#WRITES}: data sets named in {@code DD DSN=},
 *       classified by the {@code DISP} status: {@code SHR} reads; {@code NEW}, {@code MOD},
 *       {@code OLD} and an omitted status write.</li>
 * </ul>
 * The {@code DISP} mapping describes the allocation, not the I/O the program performs; it is the
 * best statement-level approximation. Temporary data sets ({@code &&}) and referbacks
 * ({@code *.}) to data sets are omitted; names containing symbolic parameters are dynamic targets.
 * Symbolic parameters are not substituted, {@code IF}/{@code THEN}/{@code ELSE} and {@code SET}
 * are not evaluated.
 */
public final class JclSourceParser implements SourceParser {

    private static final Pattern NATURAL_STACK_LOGON = Pattern.compile(
            "STACK\\s*=\\s*\\(\\s*LOGON\\s+([A-Za-z0-9_#@$-]+)[^;)]*;\\s*([A-Za-z0-9_#@$-]+)",
            Pattern.CASE_INSENSITIVE);

    @Override
    public SourceLanguage language() {
        return SourceLanguage.JCL;
    }

    @Override
    public ParsingResult parse(ParsingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.language() != SourceLanguage.JCL) {
            return ParsingResult.failure(request.source(), request.language(),
                    ParsingFailureReason.UNSUPPORTED_LANGUAGE, "JCL parser cannot parse " + request.language());
        }
        String text = request.text();
        if (text.trim().isEmpty()) {
            return ParsingResult.failure(request.source(), SourceLanguage.JCL,
                    ParsingFailureReason.EMPTY_SOURCE, "source text is empty");
        }
        if (text.indexOf('\u0000') >= 0) {
            return ParsingResult.failure(request.source(), SourceLanguage.JCL,
                    ParsingFailureReason.MALFORMED_SOURCE, "source text contains NUL characters and is not JCL");
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

    /** A component whose extent is known only after later statements. */
    private static final class Block {

        final String name;
        final CodeComponentKind kind;
        final int startLine;
        int endLine;
        CodeComponent built;

        Block(String name, CodeComponentKind kind, int startLine, int endLine) {
            this.name = name;
            this.kind = kind;
            this.startLine = startLine;
            this.endLine = endLine;
        }

        CodeComponent component() {
            if (built == null) {
                built = new CodeComponent(name, kind, new SourceLocation(startLine, endLine));
            }
            return built;
        }
    }

    /** One relation whose origin component is built at the end. */
    private static final class Draft {

        final RelationKind kind;
        final Block from;
        final CodeComponentRef target;
        final String procedureName;
        final String statement;
        final SourceLocation location;

        Draft(RelationKind kind, Block from, CodeComponentRef target, String procedureName, String statement,
              SourceLocation location) {
            this.kind = kind;
            this.from = from;
            this.target = target;
            this.procedureName = procedureName;
            this.statement = statement;
            this.location = location;
        }
    }

    /** Mutable state of a single parse; never shared. */
    private static final class Run {

        private final ParsingRequest request;
        private final String[] lines;
        private final List<SourceDiagnostic> diagnostics = new ArrayList<SourceDiagnostic>();
        private final List<Block> blocks = new ArrayList<Block>();
        private final Set<String> blockKeys = new HashSet<String>();
        private final Map<String, Block> inStreamProcedures = new HashMap<String, Block>();
        private final List<Draft> drafts = new ArrayList<Draft>();

        private Block unit;
        private Block currentJob;
        private Block currentProcedure;
        private Block currentStep;
        private boolean procedureNeedsPend;

        Run(ParsingRequest request) {
            this.request = request;
            this.lines = lines(request.text());
        }

        ProgramStructure execute() {
            List<JclStatement> statements = new JclStatementReader(lines, diagnostics).read();
            unit = register(new Block(request.unitName().toUpperCase(Locale.ROOT), unitKind(statements), 1, lines.length));
            for (JclStatement statement : statements) {
                process(statement);
            }
            if (procedureNeedsPend) {
                diagnose(SourceDiagnostic.Code.UNTERMINATED_BLOCK, currentProcedure.startLine,
                        "in-stream procedure " + currentProcedure.name + " without PEND");
                currentProcedure.endLine = lines.length;
            }
            return build();
        }

        private static CodeComponentKind unitKind(List<JclStatement> statements) {
            for (JclStatement statement : statements) {
                if (statement.operation().equals("JOB")) {
                    return CodeComponentKind.JOB;
                }
            }
            if (!statements.isEmpty() && statements.get(0).operation().equals("PROC")) {
                return CodeComponentKind.PROCEDURE;
            }
            return CodeComponentKind.COPYBOOK;
        }

        private void process(JclStatement statement) {
            String operation = statement.operation();
            if (operation.equals("JOB")) {
                processJob(statement);
            } else if (operation.equals("PROC")) {
                processProc(statement);
            } else if (operation.equals("PEND")) {
                processPend(statement);
            } else if (operation.equals("EXEC")) {
                processExec(statement);
            } else if (operation.equals("DD")) {
                extendStep(statement);
                processDd(statement);
            } else if (operation.equals("INCLUDE")) {
                extendStep(statement);
                processInclude(statement);
            } else {
                extendStep(statement);
            }
        }

        private void processJob(JclStatement statement) {
            if (procedureNeedsPend) {
                diagnose(SourceDiagnostic.Code.UNTERMINATED_BLOCK, currentProcedure.startLine,
                        "in-stream procedure " + currentProcedure.name + " without PEND");
                currentProcedure.endLine = statement.startLine() - 1;
                procedureNeedsPend = false;
            }
            currentProcedure = null;
            currentStep = null;
            if (currentJob == null && unit.kind == CodeComponentKind.JOB) {
                currentJob = unit;
                return;
            }
            String name = statement.name() != null ? statement.name() : "JOB@L" + statement.startLine();
            currentJob = register(new Block(name, CodeComponentKind.JOB, statement.startLine(), lines.length));
        }

        private void processProc(JclStatement statement) {
            currentStep = null;
            if (unit.kind == CodeComponentKind.PROCEDURE && currentProcedure == null && currentJob == null
                    && blocks.size() == 1) {
                currentProcedure = unit;
                return;
            }
            if (procedureNeedsPend) {
                diagnose(SourceDiagnostic.Code.UNTERMINATED_BLOCK, currentProcedure.startLine,
                        "in-stream procedure " + currentProcedure.name + " without PEND");
                currentProcedure.endLine = statement.startLine() - 1;
            }
            if (statement.name() == null) {
                diagnose(SourceDiagnostic.Code.UNSUPPORTED_CONSTRUCT, statement.startLine(), "in-stream PROC without name");
                currentProcedure = null;
                procedureNeedsPend = false;
                return;
            }
            Block procedure = register(new Block(statement.name(), CodeComponentKind.PROCEDURE,
                    statement.startLine(), statement.endLine()));
            if (!inStreamProcedures.containsKey(statement.name())) {
                inStreamProcedures.put(statement.name(), procedure);
            }
            currentProcedure = procedure;
            procedureNeedsPend = true;
        }

        private void processPend(JclStatement statement) {
            if (!procedureNeedsPend) {
                diagnose(SourceDiagnostic.Code.UNMATCHED_BLOCK_END, statement.startLine(), "PEND without in-stream PROC");
                return;
            }
            currentProcedure.endLine = statement.endLine();
            currentProcedure = null;
            currentStep = null;
            procedureNeedsPend = false;
        }

        private void processExec(JclStatement statement) {
            String stepName = statement.name() != null ? statement.name() : "STEP@L" + statement.startLine();
            if (currentProcedure != null && currentProcedure != unit) {
                stepName = currentProcedure.name + "." + stepName;
            }
            currentStep = register(new Block(stepName, CodeComponentKind.STEP, statement.startLine(), statement.endLine()));
            SourceLocation location = new SourceLocation(statement.startLine(), statement.endLine());

            String program = statement.keyword("PGM");
            String procedure = statement.keyword("PROC");
            if (procedure == null && program == null && !statement.positional().isEmpty()) {
                procedure = statement.positional().get(0);
            }
            if (program != null) {
                drafts.add(new Draft(RelationKind.CALLS, currentStep,
                        executableRef(program, CodeComponentKind.PROGRAM), null, "EXEC PGM", location));
            } else if (procedure != null) {
                String name = procedure.toUpperCase(Locale.ROOT);
                if (isDynamic(name)) {
                    drafts.add(new Draft(RelationKind.CALLS, currentStep,
                            CodeComponentRef.dynamic(procedure, CodeComponentKind.PROCEDURE), null, "EXEC PROC", location));
                } else {
                    drafts.add(new Draft(RelationKind.CALLS, currentStep, null, name, "EXEC PROC", location));
                }
            } else {
                diagnose(SourceDiagnostic.Code.UNSUPPORTED_CONSTRUCT, statement.startLine(),
                        "EXEC without PGM or procedure name");
            }

            Matcher stack = NATURAL_STACK_LOGON.matcher(statement.operandField());
            if (stack.find()) {
                drafts.add(new Draft(RelationKind.CALLS, currentStep, CodeComponentRef.external(
                        stack.group(2).toUpperCase(Locale.ROOT), CodeComponentKind.PROGRAM),
                        null, "NATURAL STACK LOGON", location));
            }
        }

        private void processDd(JclStatement statement) {
            String dataSet = statement.keyword("DSN");
            if (dataSet == null) {
                dataSet = statement.keyword("DSNAME");
            }
            if (dataSet == null) {
                return;
            }
            String name = JclStatementReader.stripApostrophes(dataSet).toUpperCase(Locale.ROOT);
            if (name.isEmpty() || name.startsWith("&&") || name.startsWith("*.")) {
                return;
            }
            CodeComponentRef target = name.indexOf('&') >= 0
                    ? CodeComponentRef.dynamic(dataSet, CodeComponentKind.DATA_STORE)
                    : CodeComponentRef.external(name, CodeComponentKind.DATA_STORE);
            String status = dispositionStatus(statement.keyword("DISP"));
            RelationKind kind = status.equals("SHR") ? RelationKind.READS : RelationKind.WRITES;
            drafts.add(new Draft(kind, owner(), target, null, "DD DISP=" + status,
                    new SourceLocation(statement.startLine(), statement.endLine())));
        }

        private void processInclude(JclStatement statement) {
            String member = statement.keyword("MEMBER");
            if (member == null || member.isEmpty()) {
                diagnose(SourceDiagnostic.Code.UNSUPPORTED_CONSTRUCT, statement.startLine(), "INCLUDE without MEMBER");
                return;
            }
            String name = member.toUpperCase(Locale.ROOT);
            CodeComponentRef target = isDynamic(name)
                    ? CodeComponentRef.dynamic(member, CodeComponentKind.COPYBOOK)
                    : CodeComponentRef.external(name, CodeComponentKind.COPYBOOK);
            drafts.add(new Draft(RelationKind.INCLUDES, owner(), target, null, "INCLUDE",
                    new SourceLocation(statement.startLine(), statement.endLine())));
        }

        private void extendStep(JclStatement statement) {
            if (currentStep != null) {
                currentStep.endLine = statement.endLine();
            }
        }

        /** Returns the innermost open component a DD or INCLUDE statement belongs to. */
        private Block owner() {
            if (currentStep != null) {
                return currentStep;
            }
            if (currentProcedure != null) {
                return currentProcedure;
            }
            return currentJob != null ? currentJob : unit;
        }

        private Block register(Block block) {
            String name = block.name;
            if (!blockKeys.add(block.kind + ":" + name)) {
                name = name + "@L" + block.startLine;
                blockKeys.add(block.kind + ":" + name);
                block = new Block(name, block.kind, block.startLine, block.endLine);
            }
            blocks.add(block);
            return block;
        }

        private ProgramStructure build() {
            List<CodeComponent> components = new ArrayList<CodeComponent>();
            for (Block block : blocks) {
                components.add(block.component());
            }
            List<CodeRelation> relations = new ArrayList<CodeRelation>();
            for (Draft draft : drafts) {
                CodeComponentRef target = draft.target;
                if (target == null) {
                    Block local = inStreamProcedures.get(draft.procedureName);
                    target = local != null
                            ? CodeComponentRef.local(local.name, CodeComponentKind.PROCEDURE)
                            : CodeComponentRef.external(draft.procedureName, CodeComponentKind.PROCEDURE);
                }
                relations.add(new CodeRelation(draft.kind, draft.from.component(), target, draft.statement, draft.location));
            }
            return new ProgramStructure(request.source(), SourceLanguage.JCL, components, relations, diagnostics);
        }

        private void diagnose(SourceDiagnostic.Code code, int line, String message) {
            diagnostics.add(new SourceDiagnostic(code, SourceLocation.line(line), message));
        }

        private static CodeComponentRef executableRef(String value, CodeComponentKind kind) {
            String name = value.toUpperCase(Locale.ROOT);
            if (isDynamic(name) || name.startsWith("*.")) {
                return CodeComponentRef.dynamic(value, kind);
            }
            return CodeComponentRef.external(name, kind);
        }

        private static boolean isDynamic(String name) {
            return name.indexOf('&') >= 0;
        }

        private static String dispositionStatus(String disposition) {
            if (disposition == null) {
                return "NEW";
            }
            String value = disposition.trim();
            if (value.startsWith("(")) {
                value = value.substring(1);
            }
            int end = 0;
            while (end < value.length() && Character.isLetter(value.charAt(end))) {
                end++;
            }
            String status = value.substring(0, end).toUpperCase(Locale.ROOT);
            return status.isEmpty() ? "NEW" : status;
        }
    }
}
