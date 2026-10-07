# security-prompt

Interactive secret source for Adyton (#43 slice 1). Headless and free of UI technology.

## What it does

`InteractiveSecretMaterialProvider` implements Adyton's `SecretMaterialProvider`. When the broker
needs a secret, it asks a person through the narrow `SecretPromptPort` callback and turns the answer
into short-lived `SecretMaterial` owned by the broker.

| Prompt outcome | Result for the caller | May a source chain fall through? |
|---|---|---|
| `SUPPLIED` | `SecretMaterial` (prompt copy wiped before return) | — |
| `CANCELLED` | `AuthCancelledException` | no |
| `DENIED` | `SecretReleaseDeniedException` | no |
| `UNAVAILABLE`, prompt failure, `null` result | `SecretUnavailableException` | yes |

Nobody is asked when

- the `AccessRequest` lacks an explicit purpose or scope, or
- the `InteractionGate` does not permit interaction.

`UserInitiatedInteractionGate` permits interaction only inside a permit opened on the current
thread by the code that handles an explicit user action. Background jobs and pool threads never
hold a permit, so they never open dialogs. Permits are not inherited by other threads.

## Boundaries

- `SecretPromptRequest` carries plain text only (target, principal, purpose, scope, method name),
  never a `SecretRef` or `SecretMaterial`. A UI adapter can implement `SecretPromptPort` without
  depending on Adyton.
- The provider caches nothing. Remembering a cancellation belongs to the broker cache policy.
- No Swing/AWT/JavaFX; enforced by `secretAdaptersMustStayUiFree` in the architecture tests.

## Not part of this slice

- A real UI implementation of `SecretPromptPort` (later, in Exedra).
- The ordered source chain (KeePassRPC → store → prompt): composition-root wiring.
- Persistent store, master key, DPAPI: follow a separate persistence/key decision.
