# MainframeMate → adyton Migration Inventory

This document records the inspection of MainframeMate credential/security source
files (under `research/`) and the migration decisions for the `adyton` module.

## Analysis baseline

This inventory is now grounded in the merged authentication-flow analysis:

- [docs/analysis/mainframemate-authentication-flows.md](../analysis/mainframemate-authentication-flows.md)

That document validates the `AccessBroker` / `AuthenticationStrategy` / `AccessHandle`
model against all MainframeMate authentication flows and specifies the required
contracts for PR #14.

## Security model contrast

MainframeMate and Corenth have fundamentally different credential access models:

| | MainframeMate | Corenth (adyton) |
|---|---|---|
| **Access model** | Credentials can be resolved as raw username/password | Normal modules receive only scoped, short-lived delegated access grants via `AccessBroker` or `DelegatedAccessProvider` |
| **Connector API** | Connectors call `CredentialStore.resolve()` → `String[]{user, password}` | Connectors call `AccessBroker.withAccess()` or `.acquire()` → `AccessHandle` (protocol-specific, no password visible) |
| **Module API** | `CredentialsProvider.resolve()` → `Optional<Credentials>` (includes raw password) | `DelegatedAccessProvider.request()` → `CredentialLease` (scoped capability) |
| **Secret visibility** | Raw `String` passwords flow through `CredentialStore.resolve()`, `Credentials.getPassword()` | Secrets stay inside the vault; strategies consume them; connectors/modules receive handles/leases |
| **Lifetime** | Session cache holds credentials until application exit (no expiration) | Leases/grants expire and are rejected after their stated lifetime; cache governed by `SecretCachePolicy` |
| **Scope binding** | None — credentials grant full access once resolved | Leases/grants bound to target, principal, purpose, scope, method and TTL |
| **Adapter coupling** | KeePass, DPAPI, PowerShell directly invoked in `CredentialStore` | Adapters implement `CredentialProvider` SPI; strategies implement `AuthenticationStrategy` |
| **RAM cache** | Two caches with different keys/lifecycles (caused bugs) | Single `SecretMaterialCache` with `SecretCachePolicy` (TTL, idle timeout, shutdown clear) |

## Source files inspected

All files listed in the issue were reviewed from the `research/` directory.

## Migration inventory

| MainframeMate source file | Decision | Corenth target | Reason |
|---|---|---|---|
| `core/.../files/auth/CredentialsProvider.java` | adapt | `adyton:CredentialProvider` (adapter SPI) | Core port pattern preserved. Return type changed from `Optional<Credentials>` (raw password) to `CredentialLease` (opaque, time-limited). Clarified as adapter SPI, not module-facing. |
| `core/.../files/auth/ConnectionId.java` | adapt | `adyton:CredentialRequest`, `adyton:AccessRequest` | Connection identity concept generalised into scoped requests with target + principal + purpose + scope + method + TTL. |
| `core/.../files/auth/Credentials.java` | adapt | `adyton:CredentialRef` (vault-internal) | Principal identity preserved; raw `getPassword()` removed, replaced by opaque `SecretRef`. Marked as vault-internal — not for normal module use. |
| `core/.../files/auth/AuthCancelledException.java` | adapt | `adyton:AuthCancelledException` | Preserved as subtype of `SecretUnavailableException` → `AccessException`. Now a checked exception. Broker method signatures declare it explicitly. |
| `app/.../util/CredentialStore.java` | adapt | `adyton:SessionCredentialCache`, `adyton:SecretMaterialCache`, `adyton:AccessBroker` | Session cache concept extracted. Two-cache bug fixed via unified `SecretMaterialCache` with `SecretCachePolicy`. Global singleton removed. Broker pattern replaces direct resolution. |
| `app/.../util/SessionCipher.java` | adapt | `adyton:SecretMaterialCache` | The in-memory encryption concept is replaced by policy-driven caching with TTL/idle timeout. Per-JVM key concept preserved in cache's RAM-only constraint. |
| `app/.../util/KeePassNotAvailableException.java` | adapt | `adyton:SecretUnavailableException` | Folded into unified exception hierarchy (`AccessException` → `SecretUnavailableException`). German UI message removed. |
| `app/.../util/KeePassProvider.java` | adapter-candidate | _(future adapter module)_ | 871-line class tightly coupled to PowerShell, Settings, Swing. Would implement `CredentialProvider` adapter. |
| `app/.../util/KeePassRpcClient.java` | adapter-candidate | _(future adapter module)_ | WebSocket/SRP protocol client. Would implement both `CredentialProvider` and potentially a `DelegatedAccessProvider`. |
| `app/.../util/KeePassRpcPairingDialog.java` | do-not-copy | — | Swing UI dialog. UI has no place in the vault boundary. |
| `app/.../util/AesCryptoProvider.java` | adapter-candidate | _(future adapter module)_ | Pure-Java AES-256-GCM with file-based master key. Would implement `CredentialProvider`. |
| `app/.../util/DpapiCryptoProvider.java` | adapter-candidate | _(future adapter module)_ | Windows DPAPI via JNA. Platform-specific; optional adapter behind `CredentialProvider`. |
| `app/.../util/PowerShellCryptoProvider.java` | adapter-candidate | _(future adapter module)_ | Windows DPAPI via PowerShell (no JNA). Platform-specific; optional adapter. |
| `app/.../util/WindowsCryptoUtil.java` | adapt | `adyton:CredentialProvider` (port design), `adyton:AuthenticationStrategy` | Facade pattern over multiple crypto backends preserved as the port/adapter architecture. Concrete implementations stay in adapter modules. |
| `app/.../files/impl/auth/InteractiveCredentialsProvider.java` | adapter-candidate | _(future adapter module)_ | Interactive (UI-based) password prompt. Implements `CredentialProvider` port but requires UI layer. |
| `app/.../files/impl/auth/LoginManagerCredentialsProvider.java` | adapter-candidate | _(future adapter module)_ | Non-interactive cached lookup. Good candidate for a default `CredentialProvider` adapter. |

## New Corenth API (not directly from MainframeMate)

| Corenth type | Category | Reason |
|---|---|---|
| `AccessBroker` | new-corenth-api | Connector-facing API validated by the authentication-flow analysis. Owns lifecycle: resolve → authenticate → execute → close. |
| `AccessRequest` | new-corenth-api | Extends `CredentialRequest` with explicit `AuthenticationMethod`. |
| `AccessGrant` | new-corenth-api | Scoped grant metadata on an access handle. |
| `AccessHandle` | new-corenth-api | Protocol-specific authenticated handle (three patterns: long-lived session, connectionless header, factory-shaped). |
| `AccessOperation` | new-corenth-api | Operation callback for `withAccess`. |
| `AuthenticationStrategy` | new-corenth-api | SPI that turns secrets into protocol handles. Validated against all MainframeMate flows. |
| `AuthenticationMethod` | new-corenth-api | Discriminator for strategy selection (implicit in MainframeMate, explicit in Corenth). |
| `SecretCachePolicy` | new-corenth-api | Configurable cache behavior (TTL, idle, enabled). Fixes MainframeMate's unconfigurable cache. |
| `SecretMaterial` | new-corenth-api | Vault-internal sealed boundary type. Replaces `Credentials.getPassword()`. Never public. |
| `SecretMaterialCache` | new-corenth-api | Unified cache (fixes two-cache bug). Policy-driven. RAM-only. |
| `AccessException` | new-corenth-api | Base checked exception for broker operations. |
| `CredentialLease` | new-corenth-api | Module-facing scoped grant with scope binding, TTL, and opaque lease id. |
| `DelegatedAccessProvider` | new-corenth-api | Module-facing API for delegated operations. |
| `SecretRef` | new-corenth-api | Vault-internal opaque secret handle. Replaces raw `String` passwords. |
| `DelegatedAccessResult` | new-corenth-api | Result type for delegated operations. |

## Decisions rationale

### Do-not-copy reasoning

- **KeePassRpcPairingDialog**: Swing dialog for SRP pairing PIN entry. Adyton must remain UI-free.
- **Settings/SettingsHelper coupling**: MainframeMate reads password method from global settings JSON. Adyton uses port injection — the method is determined by which adapter is provided.
- **Global singleton pattern** (`CredentialStore` static methods): Replaced by injectable `AccessBroker` / `CredentialProvider` instances.
- **German UI messages**: Removed from exception types.

### Raw credential exposure in MainframeMate

The following MainframeMate patterns expose raw credentials:
1. `Credentials.getPassword()` returns plaintext `String`
2. `CredentialStore.resolve()` returns `String[]{user, password}`
3. `SessionCipher` encrypts passwords in memory but decrypts on every access

In Corenth, these are replaced by:
1. `CredentialRef` is vault-internal and has no password getter — only an opaque `SecretRef`
2. `AccessBroker.withAccess()` / `.acquire()` returns a protocol-specific `AccessHandle` — no raw material
3. `SecretMaterialCache` stores material with TTL and idle timeout; connectors never see it
4. Normal modules never handle `SecretRef`, `CredentialRef`, or `SecretMaterial`
5. `AuthenticationStrategy` implementations consume `SecretMaterial` but must not expose it

### API layering

```
Connectors  →  AccessBroker.withAccess() / .acquire()  →  AccessHandle (protocol-specific)
               AccessHandle.grant()                     →  AccessGrant (scoped metadata)

Modules     →  DelegatedAccessProvider.request()        →  CredentialLease (scoped grant)
               DelegatedAccessProvider.authenticate()   →  DelegatedAccessResult

Vault SPI   →  CredentialProvider.acquire()             →  CredentialLease
               AuthenticationStrategy.authenticate()    →  AccessHandle
               SecretMaterial / SecretRef               →  vault-internal
```

### Future adapter work (open issues)

| Deferred work | Why |
| --- | --- |
| KeePassRPC client adapter | ~1000 lines including AES key-exchange, pairing. Separate `adyton-keepass-rpc` module. |
| PowerShell KeePass adapter | Windows-only, subprocess-heavy. Separate `adyton-keepass-ps` module. |
| DPAPI provider (JNA) | Platform-specific native binding. `adyton-dpapi-jna` module. |
| PowerShell DPAPI provider | Same, subprocess-based. `adyton-dpapi-ps` module. |
| AES file-key provider | Portable. `adyton-aes` module. |
| FTP `AuthenticationStrategy` | Lives next to the FTP connector; needs commons-net. |
| NDV `AuthenticationStrategy` | Lives next to NDV; hides `getPassword()` from non-strategy code. |
| MediaWiki `AuthenticationStrategy` | Lives next to the wiki module; wraps `MediaWikiBot`. |
| Confluence Basic Auth strategy | Belongs in confluence module. |
| Confluence mTLS strategy | Same module; Windows-MY specifics stay there. |
| SharePoint SSO strategy | Same module. |
| Interactive prompt provider | UI module (Swing / future GUI), never in adyton. |
| `LoginManager` migration | Retry/block behavior on top of the broker as a separate UX concern. |
