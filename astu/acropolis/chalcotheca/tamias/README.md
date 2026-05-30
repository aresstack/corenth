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

## Architecture rule

Do **not** expose Holkas as a general client-facing API. Callers must access resources through the Chalcotheca mediated resource service, which uses Tamias for every access decision.

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.
