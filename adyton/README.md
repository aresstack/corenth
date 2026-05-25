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

## Analysis baseline

The design is validated against the MainframeMate authentication flow analysis:

- [Authentication flow analysis](../docs/analysis/mainframemate-authentication-flows.md)
- [Migration inventory](../docs/migration/mainframemate-adyton-inventory.md)

## Five-concept model

| Concept | Description |
|---------|-------------|
| **Secret source** | Where credentials come from (KeePass, encrypted file, session prompt, OS store) |
| **Secret material cache** | RAM-only cache inside adyton, optional and configurable via `SecretCachePolicy` |
| **Authentication strategy** | How a specific protocol turns secrets into access (FTP login, Wiki API, Basic Auth, mTLS) |
| **Access broker** | Controls short-lived use and lifecycle — the connector-facing API |
| **Access handle / grant** | The protocol-specific thing connectors operate with |

## API layering

```
Connectors  →  AccessBroker.withAccess() / .acquire()  →  AccessHandle (protocol-specific)
               AccessHandle.grant()                     →  AccessGrant (scoped metadata)

Modules     →  DelegatedAccessProvider.request()        →  CredentialLease (scoped grant)
               DelegatedAccessProvider.authenticate()   →  DelegatedAccessResult

Vault SPI   →  CredentialProvider.acquire()             →  CredentialLease
               AuthenticationStrategy.authenticate()    →  AccessHandle
               SecretMaterial / SecretRef               →  vault-internal identifiers
```

## Connector-facing API (AccessBroker)

Connectors use `AccessBroker` — they never resolve passwords directly:

```java
// Safe default: broker manages handle lifecycle
String result = broker.withAccess(
    new AccessRequest(new SecretRef("keepass://ftp/mainframe"),
                      "ftp:mainframe", "BATCH_USER", "nightly-job",
                      "upload-jcl", AuthenticationMethod.FTP_PASSWORD, 300000L),
    ftpStrategy,
    handle -> {
        // handle is an authenticated FTP session — no password visible
        return handle.upload(file);
    });

// Long-lived handle for reuse (Wiki search-as-you-type, NDV repeated calls)
WikiHandle wiki = broker.acquire(
    new AccessRequest(new SecretRef("keepass://wiki/internal"),
                      "wiki:internal", "svc", "search",
                      "read", AuthenticationMethod.MEDIA_WIKI_LOGIN, 600000L),
    wikiStrategy);
try {
    wiki.search("query1");
    wiki.search("query2");
} finally {
    wiki.close();
}
```

## Module-facing API (DelegatedAccessProvider)

Normal modules that do not implement protocol-specific connectors use
`DelegatedAccessProvider`:

```java
CredentialRequest req = new CredentialRequest(
    "mainframe", "BATCH_USER", "nightly-job", "submit-jcl", 300000L);
CredentialLease lease = delegatedAccessProvider.request(req);
DelegatedAccessResult result = delegatedAccessProvider.authenticate(lease, "host:3270");
delegatedAccessProvider.revoke(lease);
```

## Vault-internal types

`SecretRef`, `CredentialRef` and `SecretMaterialCache` are for vault internals and
trusted credential adapters only. Normal modules and connectors never handle these types.

`SecretMaterial` is an interface accessible to `AuthenticationStrategy` implementations
(which may reside in external adapter modules/packages). Strategy code can call
`material.principal()` and `material.secret()` to perform protocol-specific
authentication. Construction of `SecretMaterial` instances remains internal to adyton.

## Credential reference vs. target system

`AccessRequest` and `CredentialRequest` distinguish between:
- **`credentialRef`** — tells adyton *where to find* the credential (e.g., `keepass://wiki/internal`)
- **`targetSystem`** — tells adyton *what the credential is used for* (e.g., `https://wiki.example.internal`)

Multiple credential entries may point to the same target, and the same credential entry
may be reused for several scoped requests. Connector configurations specify both:

```text
baseUrl: https://wiki.example.internal
credentialRef: keepass://wiki/internal
method: mediawiki-login
```

## RAM cache

Adyton may cache secret material in memory, but only inside the vault boundary.
Normal modules still receive scoped access handles / grants / operations, not passwords.

Cache properties (governed by `SecretCachePolicy`):
- **RAM-only** — never persisted to disk.
- **Configurable** — users/admins choose whether caching is enabled.
- **Time-bound** — TTL cap (default 60 min) and idle timeout (default 10 min).
- **Clearable** — explicit revoke, `revokeAll(target)`, and clear on shutdown.
- **Scoped keying** — entries keyed by (target, principal, purpose, scope, method).
- **No logging** — never logs usernames + secret values together.

## Handle patterns

Two patterns discovered in the MainframeMate analysis:

1. **Long-lived session** — FTP client, NDV connection, Wiki cookie. Obtained via
   `broker.acquire()`, reused across many operations, closed on revoke or timeout.
2. **Derived material** — Confluence Basic Auth header, mTLS `SSLContext`.
   Password is needed once to derive the handle, then can be discarded.

Protocols that require raw secret material at connect time (FTP, NDV) confine that
exposure to the `AuthenticationStrategy` implementation — connectors never see it.

## Type derivation table

| Type | Derived from | Role |
|------|---|------|
| `AccessBroker` | _new_ (analysis-validated) | Connector-facing API with `withAccess` + `acquire` |
| `AccessRequest` | `CredentialRequest` + `AuthenticationMethod` | Scoped request with target, principal, purpose, scope, method, TTL |
| `AccessGrant` | _new_ (grant/capability concept) | Scoped metadata on an access handle |
| `AccessHandle` | _new_ (protocol handle concept) | Protocol-specific authenticated handle |
| `AccessOperation` | _new_ | Operation callback for `withAccess` |
| `AuthenticationStrategy` | _new_ (analysis-validated) | SPI: turns secrets into protocol handles |
| `AuthenticationMethod` | _new_ (implicit in MainframeMate) | Discriminator for strategy selection |
| `SecretCachePolicy` | `CredentialStore` cache semantics | Configurable TTL/idle/enabled policy |
| `SecretMaterial` | `SessionCipher` decrypted output | Vault-internal secret container |
| `SecretMaterialCache` | `CredentialStore.sessionCache` + `LoginManager.sessionPasswordCache` | Unified, policy-driven internal cache |
| `CredentialLease` | _new_ (session cache + scope) | Module-facing scoped grant |
| `CredentialRequest` | `ConnectionId` | Module-facing scoped request |
| `DelegatedAccessProvider` | _new_ (KeePassRpcClient precedent) | Module-facing delegated operations |
| `DelegatedAccessResult` | _new_ | Outcome of a delegated operation |
| `CredentialProvider` | `CredentialsProvider` | Adapter SPI — trusted backends only |
| `SecretRef` | _new_ (replaces raw passwords) | Vault-internal opaque secret handle |
| `CredentialRef` | `Credentials` | Vault-internal principal+secret pair |
| `SessionCredentialCache` | `CredentialStore.sessionCache` + `SessionCipher` | In-memory lease cache (auto-expires) |
| `AccessException` | _new_ | Base checked exception for broker operations |
| `SecretUnavailableException` | `KeePassNotAvailableException` et al. | Unified "access denied or unavailable" |
| `AuthCancelledException` | `AuthCancelledException` | User-initiated cancellation (subtype) |

## Design principles

- **Delegated access is the primary contract.** Connectors receive protocol-specific handles; modules receive scoped grants — never raw credentials.
- **No raw secrets cross the boundary.** Callers never receive plaintext credentials.
- **Leases and grants are purpose- and scope-bound.** Tied to target, principal, purpose and operation scope.
- **Time-limited.** Expired grants/leases are automatically rejected.
- **SecretMaterial and SecretRef are vault-internal.** Only trusted strategies/adapters handle them.
- **RAM cache is supported but controlled.** Policy-driven TTL, idle timeout, configurable, clearable.
- **Adapters are replaceable.** KeePass, DPAPI, OS stores, environment variables plug in behind the SPI.
- **No UI or application coupling.** Zero dependencies on Swing, application settings, or singletons.
- **`getPassword()` is forbidden.** No public API exposes raw passwords.

## Adapter guidance

Platform-specific backends and protocol strategies should be implemented as
separate adapter modules. They must not be hardwired into this core module.

Deferred adapter work (from the analysis):
- FTP `AuthenticationStrategy` + `FtpAccessHandle`
- NDV `AuthenticationStrategy` + `NdvAccessHandle`
- Wiki `AuthenticationStrategy` + `WikiAccessHandle`
- Confluence Basic Auth / mTLS strategies
- KeePassRPC adapter implementing `CredentialProvider`
- DPAPI/PowerShell adapter implementing `CredentialProvider`
- AES file-key adapter implementing `CredentialProvider`
- Interactive prompt adapter (UI module, not in adyton)

## Role in Corenth

This module is part of the Corenth Gradle multi-project architecture and keeps its Greek name intentionally. The name marks a boundary in the architecture and should not be replaced by a generic technical term.

