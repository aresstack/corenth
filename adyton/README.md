# Adyton

Security vault boundary for credentials, keys and delegated access.

Adyton is the isolated vault of Corenth. It represents the boundary where credentials, keys and delegated access operations belong. Ordinary application logic should not reach into this module to read secrets. It should ask for controlled actions or signed/delegated access instead.

## Credential Boundary API

The API models secret access without ever exposing raw credential material:

| Type | Role |
|------|------|
| `SecretRef` | Opaque handle to a secret — never reveals the value |
| `CredentialRef` | Pairs a principal identity with a `SecretRef` |
| `CredentialRequest` | Describes what a module needs (target system + principal) |
| `CredentialLease` | Time-limited, revocable grant returned by the vault |
| `CredentialProvider` | Port that resolves requests into leases |
| `DelegatedAccessProvider` | Port for vault-mediated operations (no raw secrets leave the boundary) |
| `DelegatedAccessResult` | Outcome of a delegated operation |
| `SessionCredentialCache` | In-memory, session-scoped lease cache (never persisted) |
| `SecretUnavailableException` | Thrown when access is denied or unavailable |

### Design principles

- **No raw secrets cross the boundary.** Callers receive opaque references and leases, never plaintext credentials.
- **Leases are time-limited.** Expired leases are automatically discarded.
- **Adapters are replaceable.** KeePass, DPAPI, OS credential stores, or environment-variable backends plug in behind the `CredentialProvider` / `DelegatedAccessProvider` ports without changing the core API.
- **No UI or application coupling.** The module has zero dependencies on Swing, application settings, or framework singletons.

### Adapter guidance

Platform-specific backends (KeePassRPC, Windows DPAPI, PowerShell credential vaults) should be implemented as separate adapter modules that depend on `adyton` and implement `CredentialProvider` or `DelegatedAccessProvider`. They must not be hardwired into this core module.

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.

