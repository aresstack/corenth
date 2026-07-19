# Proasteion

Outer ring for ports and adapters.

Proasteion is the outer ring around the core. It contains adapters that translate the outside world into Corenth concepts. Adapter modules may depend inward, but the inner city must not depend outward.

## Allowed dependency direction

```text
proasteion adapters -> astu / adyton contracts
astu / adyton       -X-> proasteion
```

Concrete adapter families remain independently understandable and depend only on the inward contracts they actually need:

- `exedra` — local UI adapter and generic shell; it calls inward use-case ports and must not own domain or policy decisions.
- `katagogion` — plugin/tool adapter boundary; extensions may register only through stable Corenth ports and must not bypass policy or mediated access.
- `emporion` — harbor/composition boundary for transported resources.
- `emporion:holkas` — raw acquisition connectors behind Chalcotheca's `AcquisitionPort`; clients must not call Holkas directly.
- `emporion:deigma` — shallow content detection and extraction; it must not own transport, archive, policy or indexing.
- `platform:*` — operating-system and infrastructure adapters such as network routing and trusted secret providers.

These rules are enforced in `architecture-tests`, including the prohibition on inner-city dependencies on `proasteion` and the mediated-resource-access rules for client adapters.

## Shared adapter vocabulary

Corenth does not currently define speculative root abstractions such as `OuterAdapter`, `AdapterKind` or `AdapterCapability`. The existing adapter families have not required a common runtime vocabulary, and their architectural boundary is already explicit and enforced.

A shared abstraction should be introduced only when at least two concrete adapter families have the same demonstrated registration or lifecycle need. Until then, separate focused contracts avoid a generic adapter hierarchy without behavior.

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.
