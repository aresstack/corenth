# Propylaea

Gate for deep source-code parsing and language abstraction.

Propylaea turns the text of a source unit into a language-neutral program structure: the
components the unit defines and the typed relations they originate (calls, includes, data
access), each with line provenance. It is deliberately **not** general file ingestion: it never
reads resources, detects file types or extracts text. The caller hands in text that the shallow
extraction stage (Deigma, `ContentCategory.SOURCE_CODE`) already produced, together with the
`VirtualResourceRef` it came from.

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek name
intentionally. The name marks a boundary in the architecture and should not be replaced by a
generic technical term.

Propylaea depends only on `astu`. The architecture rule `propylaeaMustStayAPureSourceGate`
forbids dependencies on Acropolis (including Chalcotheca, Tamias, Anagraphai, Pinakes),
Proasteion, Adyton, `java.nio.file` and `java.net`.

## Model (`com.aresstack.corenth.astu.propylaea`)

| Type | Meaning |
| --- | --- |
| `SourceLanguage` | `NATURAL`, `COBOL`, `JCL`, `UNKNOWN` |
| `SourceLocation` | inclusive 1-based line range |
| `CodeComponent` / `CodeComponentKind` | element defined in the unit: `PROGRAM`, `SUBROUTINE`, `JOB`, `STEP`, `PROCEDURE`, `COPYBOOK`, `DATA_AREA`, `DATA_STORE` |
| `CodeRelation` / `RelationKind` | typed edge `CALLS`, `INCLUDES`, `READS`, `WRITES` from a component, with the language keyword (`statement`) and location |
| `CodeComponentRef` | relation target: `LOCAL` (defined in this unit), `EXTERNAL` (named statically) or `DYNAMIC` (computed at run time; the expression is kept verbatim) |
| `SourceDiagnostic` | machine-readable remark (`UNTERMINATED_LITERAL`, `UNTERMINATED_BLOCK`, `UNMATCHED_BLOCK_END`, `UNSUPPORTED_CONSTRUCT`, `UNRESOLVED_REFERENCE`) |
| `ProgramStructure` | immutable result; the first component is the unit; components, relations and diagnostics keep source order |
| `ParsingRequest` / `ParsingResult` / `ParsingFailureReason` | input (provenance, unit name, language hint, text) and typed outcome (`UNSUPPORTED_LANGUAGE`, `EMPTY_SOURCE`, `MALFORMED_SOURCE`) |
| `SourceParser` | port implemented by every language parser |
| `SourceParserRegistry` | immutable dispatch, one parser per language, detection for `UNKNOWN` hints |
| `SourceLanguageDetector` / `KeywordSourceLanguageDetector` | language guess from unambiguous structural keywords |

Rules that hold for every parser:

- Partially understood input is a success with diagnostics; only input that yields no structure
  at all is a failure. Parsers do not throw for malformed source.
- Names are normalised to upper case. Every occurrence is its own relation; consumers deduplicate.
- Equal input yields an equal structure (line endings `\n`, `\r\n` and `\r` are equivalent).
- A parser never resolves an external name to a resource. Mapping `EXTERNAL` targets to
  resources and building a cross-resource dependency graph is a later Acropolis step.

## Reference parsers

The parsers are line- or token-oriented relation extractors, not compilers. They are optional
plug-ins: nothing registers them implicitly; a composition root passes the ones it wants to
`SourceParserRegistry`. None has a third-party dependency.

### Natural (`natural.NaturalSourceParser`), structured mode

- Comments: lines starting with `**`, `/*` or `* `; `/*` outside a constant ends the line.
  Constants in `'…'` or `"…"` with doubled delimiters.
- Statements are recognised at the start of a line, after an optional label (`R1.`) and an
  optional `THEN`/`ELSE`.
- Components: the unit (`PROGRAM`) and `DEFINE SUBROUTINE … END-SUBROUTINE` blocks.
- `CALLS`: `CALLNAT`, `FETCH [RETURN|REPEAT]`, `CALL [FILE|LOOP]` (constant operand: external,
  variable operand: dynamic); `PERFORM` (local if the subroutine is defined in the unit,
  otherwise external). `PERFORM BREAK` is ignored.
- `INCLUDES`: `INCLUDE` copycode; `LOCAL|PARAMETER|GLOBAL USING` data areas.
- `READS`: `READ`, `FIND` (incl. `FIRST`/`NUMBER`/`UNIQUE`), `HISTOGRAM`, `GET`.
  `WRITES`: `STORE`, `UPDATE`, `DELETE`. The target is the DDM of the local `VIEW OF`
  definition; a view defined elsewhere (for example in a data area) is used by name with an
  `UNRESOLVED_REFERENCE` diagnostic. `UPDATE`/`DELETE` bind to the referenced label or the
  innermost open `READ`/`FIND`/`HISTOGRAM` loop.
- Not modelled: statements that do not start a line, reporting mode, `READ WORK FILE`,
  maps (`INPUT USING MAP`), `DEFINE` without the `SUBROUTINE` keyword, source line numbers in the
  text, anything after the program-ending `END`.
- Deviation from the MainframeMate evidence: an unquoted `CALLNAT`/`FETCH`/`CALL` operand is a
  variable, so it is reported as `DYNAMIC` instead of as a program name.

### JCL (`jcl.JclSourceParser`)

- Card images: columns 72-80 ignored, `//*` comments, operand-field comments, comma and
  apostrophe continuations, `DD *` / `DD DATA` / `DLM=` in-stream data.
- Unit kind: `JOB` if the member contains a `JOB` statement, `PROCEDURE` for a cataloged
  procedure (first statement `PROC`), otherwise `COPYBOOK` (include group). The first job maps
  to the unit; later jobs, in-stream procedures and steps are own components. Steps of in-stream
  procedures are named `PROC.STEP`; unnamed steps `STEP@L<n>`; duplicate names get `@L<n>`.
- `CALLS`: `EXEC PGM=` (referback and symbolic: dynamic), `EXEC PROC=` / `EXEC name` (local for
  in-stream procedures), Natural program in `STACK=(LOGON library;program)`.
- `INCLUDES`: `INCLUDE MEMBER=`.
- `READS`/`WRITES`: `DD DSN=` by `DISP` status: `SHR` reads; `NEW`, `MOD`, `OLD` and an omitted
  status write. This describes the allocation, not the I/O the program performs. Temporary data
  sets (`&&`) and `*.` referbacks are omitted; symbolic names are dynamic.
- Not modelled: symbolic substitution, `SET`, `IF`/`THEN`/`ELSE`, `JCLLIB` search order,
  JES control statements.

### COBOL (`cobol.CobolSourceParser`), fixed reference format

- Sequence area (1-6) and identification area (73-80) ignored; indicator `*`, `/` and `D`
  (debugging lines, treated as comments) skip the line; `-` continues a literal; `*>` floating
  comments.
- Components: the unit (`PROGRAM`) and procedure-division sections and paragraphs
  (`SUBROUTINE`), recognised as a name in area A followed by a period or `SECTION`.
- `CALLS`: `CALL` (literal: external, identifier: dynamic), `PERFORM name [THRU name]` (local;
  an unknown target is diagnosed and omitted; inline `PERFORM … TIMES/UNTIL/VARYING/WITH TEST` is
  not a relation), `EXEC CICS LINK|XCTL PROGRAM(…)`.
- `INCLUDES`: `COPY` (anywhere) and `EXEC SQL INCLUDE`.
- Not modelled: free format (`>>SOURCE FORMAT FREE` stops analysis with a diagnostic), `GO TO`,
  file and SQL table access, nested programs (attributed to the unit), `REPLACE`/`REPLACING`
  effects.

## Roadmap

1. Wire the registry behind Deigma's `SOURCE_CODE` category in the composition root and feed
   relations to Anagraphai/Pinakes (coordinator scope, not part of this module).
2. Cross-resource resolution of `EXTERNAL` targets and dependency graph in Acropolis.
3. Optional grammar-based adapters (for example the ANTLR JCL grammar from the research tree)
   as separate submodules behind the same `SourceParser` port, if the line-oriented extractors
   prove insufficient.
4. Visualisation (for example Mermaid) as an outer adapter over `ProgramStructure`.

Not copied from MainframeMate: `JclOutlineModel`/`JclElementType` (UI outline model), token
makers, syntax highlighting and Swing components.
