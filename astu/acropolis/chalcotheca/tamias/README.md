# Tamias

Rights and cache-policy steward — the guardian at the bronze archive counter.

Tamias is the steward of access and cache decisions. It is responsible for policy checks such as whitelists, blacklists, user visibility, TTLs and cache invalidation rules.

## Mediated access model

Every operation requested by a caller (user, bot, service) passes through Tamias before the bronze archive fulfils or denies the request. Tamias evaluates a `ResourceAccessRequest` and returns a `ResourceAccessDecision`.

### Key types

| Type | Purpose |
|------|---------|
| `ActorIdentity` | Identifies the caller (subjectId, actorType, displayName, roles) |
| `ActorType` | HUMAN, BOT, or SERVICE |
| `ResourceOperation` | Stable set of operations (LIST_CHILDREN, READ_CONTENT, etc.) |
| `ResourceAccessRequest` | A request combining actor, target URI, and operation |
| `ResourceAccessDecision` | The outcome (ALLOW, DENY, REQUIRE_AUTH, etc.) with reason code |
| `AccessDecisionType` | ALLOW, DENY, REQUIRE_AUTH, REQUIRE_SOURCE_CHECK, ALLOW_CACHED_ONLY |
| `AccessReasonCode` | Stable reason codes (ALLOWED, BLACKLISTED, BOT_RESTRICTED, etc.) |
| `ResourceAccessPolicy` | Port for evaluating mediated access decisions |

### Existing indexing policy types (retained)

| Type | Purpose |
|------|---------|
| `ResourcePolicy` | Port for evaluating indexing acceptance (pattern-based) |
| `PatternResourcePolicy` | Pattern/rule-based implementation |
| `IndexingRule` | A single include/exclude rule |
| `AcceptanceDecision` | ACCEPT or DENY for indexing |
| `PolicyReason` | Reason for an indexing decision |

## Lifecycle policies (#5)

Tamias decides what should happen to a resource from facts it is handed; it reads no archive, holds no payload, no history and no cache presence, and executes nothing. Chalcotheca (#33) owns the facts, Acropolis (#10) maps them into the inputs below and executes the resulting decisions.

Tamias does not import Chalcotheca (`TAMIAS_MUST_STAY_POLICY_STEWARD`; the Gradle dependency already runs from Chalcotheca to Tamias). The inputs are therefore a small, immutable, non-persisted projection, and digest comparison stays with the canonical `ResourceDigest` equality on the Chalcotheca side.

### Scope, traversal and size (`tamias.scope`)

| Type | Purpose |
|------|---------|
| `ResourceScope` | Subtree at and below one root `BookmarkUri`; lexical containment on scheme, authority and whole path segments; depth below the root |
| `TraversalPolicy` | Maximum depth below a scope root (`evaluate` for a resource, `evaluateDescent` for listing a container's children) |
| `ResourceSizePolicy` | Size limit for one operation; an unknown size is `UNDETERMINED`, never zero bytes |
| `ScopeDecision` / `ScopeReasonCode` / `ScopeVerdict` | Immutable outcome; each reason code implies one verdict (`ADMIT`, `REJECT`, `UNDETERMINED`) |

Include/exclude patterns stay with `PatternResourcePolicy`/`IndexingRule`; callers compose scope, patterns and size. Since #10 Slice 5 the production composition sets no `IndexingRule.maxBytes`; the size limit is the `ResourceSizePolicy` that the lifecycle applies to metadata, to the bounded acquisition and to the acquired size. `IndexingRule.maxBytes` keeps its semantics for existing callers.

### Change detection (`tamias.change`)

| Type | Purpose |
|------|---------|
| `ResourceRecordFacts` | Projection of an `ArchivedResource`: latest observed sequence, indexed sequence or `NOT_INDEXED`, removed at source |
| `SourceObservation` / `ContentComparison` | Present (with the digest comparison against the latest observed version, computed by #10) or absent |
| `ChangeDetectionStrategy` / `DigestChangeDetection` | Pure decision `NEW`, `UNCHANGED`, `CHANGED`, `REMOVED`, `NOT_FOUND` |
| `ChangeDecision` / `ChangeReasonCode` / `ChangeKind` | Immutable outcome carrying its inputs; each reason code implies one kind |

Change is measured against the latest **observed** version, not the indexed one. `UNCHANGED` is a statement about content only and does not mean "nothing to do".

### Derivative disposition (`tamias.disposition`)

| Type | Purpose |
|------|---------|
| `DerivativeDispositionPolicy` | Maps a `ChangeDecision` to separate cache and index decisions; `decideNotAdmitted` maps a resource-level rejection (`PolicyReason` `DENY`, rejected `ScopeDecision`) to `RETAIN/NOT_ADMITTED` and `WITHDRAW/NOT_ADMITTED_WHILE_INDEXED`, `NONE/NOT_ADMITTED_NOT_INDEXED` or `WITHDRAW/NOT_ADMITTED_UNRECORDED` (#10 Slice 5). Actor- or request-specific access decisions never reach it. |
| `DerivativeDisposition` | Immutable plan: `CacheAction` (`RETAIN`, `REFRESH`, `INVALIDATE`) and `IndexAction` (`INDEX`, `REINDEX`, `RETAIN`, `WITHDRAW`, `NONE`) with `CacheReasonCode`/`IndexReasonCode` |

Key cases: unchanged content without an indexed fact, or with an older indexed version, requires `REINDEX`; a tombstoned resource is withdrawn only if a version is still indexed.

## Architecture rule

Do **not** expose Holkas as a general client-facing API. Callers must access resources through the Chalcotheca mediated resource service, which uses Tamias for every access decision.

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.
