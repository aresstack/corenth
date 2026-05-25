# Adyton

Security vault boundary for credentials, keys and delegated access.

Adyton is the isolated vault of Corenth. It represents the boundary where credentials, keys and delegated access operations belong. Ordinary application logic should not reach into this module to read secrets. It should ask for controlled actions or signed/delegated access instead.

## Security model

The access model for Corenth is not:

```
Give me username/password.
```

It is:

```
Give me a delegated, short-lived access capability for this target, purpose and scope.
```

Normal Corenth modules (`holkas`, `deigma`, `tamias`, `acropolis`, UI code) receive
only scoped, time-limited access grants — never raw credentials or secret references.

## Credential Boundary API

The API models delegated access without ever exposing raw credential material.
It is derived from the MainframeMate credential infrastructure (see
[migration inventory](../docs/migration/mainframemate-adyton-inventory.md))
but adapted to Corenth's stricter boundary model.

### Module-facing API

Normal modules use `DelegatedAccessProvider`:

```java
// Request a scoped, time-limited access grant
CredentialRequest req = new CredentialRequest(
    "mainframe", "BATCH_USER", "nightly-job", "submit-jcl", 300000L);
CredentialLease lease = delegatedAccessProvider.request(req);

// Perform delegated operations — secrets stay inside the vault
DelegatedAccessResult result = delegatedAccessProvider.authenticate(lease, "host:3270");

// Revoke when done
delegatedAccessProvider.revoke(lease);
```

### Adapter SPI (vault internals)

Trusted credential backends implement `CredentialProvider`:

```java
// Adapter-level: resolves requests into leases using backend-specific logic
CredentialLease lease = credentialProvider.acquire(request);
credentialProvider.release(lease);
```

`SecretRef` and `CredentialRef` are for vault internals and trusted credential
adapters only. Normal modules never handle these types.

### Type derivation table

| Type | Derived from | Role |
|------|---|------|
| `CredentialLease` | _new_ (session cache + scope concept) | Primary access grant — scoped, time-limited, carries target/principal/purpose/scope |
| `CredentialRequest` | `ConnectionId` | Scoped request with target, principal, purpose, scope, requested TTL |
| `DelegatedAccessProvider` | _new_ (KeePassRpcClient precedent) | Module-facing API for delegated operations |
| `DelegatedAccessResult` | _new_ | Outcome of a delegated operation |
| `CredentialProvider` | `CredentialsProvider` | Adapter SPI — trusted backends only |
| `SecretRef` | _new_ (replaces raw passwords) | Vault-internal opaque secret handle |
| `CredentialRef` | `Credentials` | Vault-internal principal+secret pair |
| `SessionCredentialCache` | `CredentialStore.sessionCache` + `SessionCipher` | In-memory lease cache (never persisted, auto-expires) |
| `SecretUnavailableException` | `AuthCancelledException`, `KeePassNotAvailableException` | Unified "access denied or unavailable" |
| `AuthCancelledException` | `AuthCancelledException` | User-initiated cancellation (subtype) |

### Design principles

- **Delegated access is the primary contract.** Normal modules receive scoped, short-lived grants — not credential handles.
- **No raw secrets cross the boundary.** Callers never receive plaintext credentials.
- **Leases are purpose- and scope-bound.** Each grant is tied to a specific target, principal, purpose and operation scope.
- **Leases are time-limited.** Expired leases are automatically rejected.
- **SecretRef is vault-internal.** Only trusted adapters handle secret references; normal modules use `DelegatedAccessProvider`.
- **Adapters are replaceable.** KeePass, DPAPI, OS credential stores, or environment-variable backends plug in behind the adapter SPI without changing the module-facing API.
- **No UI or application coupling.** The module has zero dependencies on Swing, application settings, or framework singletons.

### Adapter guidance

Platform-specific backends (KeePassRPC, Windows DPAPI, PowerShell credential vaults) should be implemented as separate adapter modules that depend on `adyton` and implement `CredentialProvider`. They must not be hardwired into this core module.

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.

