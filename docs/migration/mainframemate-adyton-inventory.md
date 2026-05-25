# MainframeMate → adyton Migration Inventory

This document records the inspection of MainframeMate credential/security source
files (under `research/`) and the migration decisions for the `adyton` module.

## Security model contrast

MainframeMate and Corenth have fundamentally different credential access models:

| | MainframeMate | Corenth (adyton) |
|---|---|---|
| **Access model** | Credentials can be resolved as raw username/password | Normal modules receive only scoped, short-lived delegated access grants |
| **Module-facing API** | `CredentialsProvider.resolve()` → `Optional<Credentials>` (includes raw password) | `DelegatedAccessProvider.request()` → `CredentialLease` (scoped capability) |
| **Secret visibility** | Raw `String` passwords flow through `CredentialStore.resolve()`, `Credentials.getPassword()` | Secrets stay inside the vault; modules receive opaque leases |
| **Lifetime** | Session cache holds credentials until application exit (no expiration) | Leases expire and are rejected after their stated lifetime |
| **Scope binding** | None — credentials grant full access once resolved | Leases are bound to target, principal, purpose, scope and TTL |
| **Adapter coupling** | KeePass, DPAPI, PowerShell directly invoked in `CredentialStore` | Adapters implement `CredentialProvider` SPI; modules use `DelegatedAccessProvider` |

## Source files inspected

All files listed in the issue were reviewed from the `research/` directory.

## Migration inventory

| MainframeMate source file | Decision | Corenth target | Reason |
|---|---|---|---|
| `core/.../files/auth/CredentialsProvider.java` | adapt | `adyton:CredentialProvider` (adapter SPI) | Core port pattern preserved. Return type changed from `Optional<Credentials>` (raw password) to `CredentialLease` (opaque, time-limited). Clarified as adapter SPI, not module-facing. |
| `core/.../files/auth/ConnectionId.java` | adapt | `adyton:CredentialRequest` | Connection identity concept generalised into a scoped request with targetSystem + principal + purpose + scope + requestedTtlMillis. |
| `core/.../files/auth/Credentials.java` | adapt | `adyton:CredentialRef` (vault-internal) | Principal identity preserved; raw `getPassword()` removed, replaced by opaque `SecretRef`. Marked as vault-internal — not for normal module use. |
| `core/.../files/auth/AuthCancelledException.java` | adapt | `adyton:AuthCancelledException` | Preserved as subtype of `SecretUnavailableException`. Now a checked exception. |
| `app/.../util/CredentialStore.java` | adapt | `adyton:SessionCredentialCache`, `adyton:CredentialProvider` | Session cache concept extracted (RAM-only, never persisted). Global singleton + Settings coupling removed. KeePass delegation and DPAPI calls become adapter candidates. |
| `app/.../util/SessionCipher.java` | adapt | `adyton:SessionCredentialCache` | The in-memory encryption concept is replaced by storing only opaque lease references with expiration. No encrypted secrets in memory. |
| `app/.../util/KeePassNotAvailableException.java` | adapt | `adyton:SecretUnavailableException` | Folded into unified exception. German UI message removed. |
| `app/.../util/KeePassProvider.java` | adapter-candidate | _(future adapter module)_ | 871-line class tightly coupled to PowerShell, Settings, Swing. Useful KeePass integration logic should become a `CredentialProvider` adapter in a separate module. |
| `app/.../util/KeePassRpcClient.java` | adapter-candidate | _(future adapter module)_ | WebSocket/SRP protocol client. Could implement `DelegatedAccessProvider` for KeePassRPC operations. Requires external dependencies (Gson, java-websocket). |
| `app/.../util/KeePassRpcPairingDialog.java` | do-not-copy | — | Swing UI dialog. UI has no place in the vault boundary. |
| `app/.../util/AesCryptoProvider.java` | adapter-candidate | _(future adapter module)_ | Pure-Java AES-256-GCM with file-based master key. Useful as a `CredentialProvider` adapter for persistent encryption on non-Windows platforms. |
| `app/.../util/DpapiCryptoProvider.java` | adapter-candidate | _(future adapter module)_ | Windows DPAPI via JNA. Platform-specific; model as optional adapter behind `CredentialProvider`. |
| `app/.../util/PowerShellCryptoProvider.java` | adapter-candidate | _(future adapter module)_ | Windows DPAPI via PowerShell (no JNA). Platform-specific; optional adapter. |
| `app/.../util/WindowsCryptoUtil.java` | adapt | `adyton:CredentialProvider` (port design) | Facade pattern over multiple crypto backends preserved as the port/adapter architecture of `CredentialProvider`. Concrete implementations stay in adapter modules. |
| `app/.../files/impl/auth/InteractiveCredentialsProvider.java` | adapter-candidate | _(future adapter module)_ | Interactive (UI-based) password prompt. Implements `CredentialProvider` port but requires UI layer. |
| `app/.../files/impl/auth/LoginManagerCredentialsProvider.java` | adapter-candidate | _(future adapter module)_ | Non-interactive cached lookup. Good candidate for a default `CredentialProvider` adapter once a credential store adapter exists. |

## New Corenth API (not directly from MainframeMate)

| Corenth type | Category | Reason |
|---|---|---|
| `CredentialLease` | new-corenth-api | Primary access grant with scope binding, TTL, and opaque lease id. MainframeMate has no equivalent — its session cache holds indefinite encrypted credentials. |
| `DelegatedAccessProvider` | new-corenth-api | Module-facing API. Replaces the direct credential resolution pattern from MainframeMate with a delegated, scoped access model. |
| `SecretRef` | new-corenth-api | Vault-internal opaque secret handle. Replaces raw `String` passwords. Not exposed to normal modules. |
| `DelegatedAccessResult` | new-corenth-api | Result type for delegated operations. |

## Decisions rationale

### Do-not-copy reasoning

- **KeePassRpcPairingDialog**: Swing dialog for SRP pairing PIN entry. Adyton must remain UI-free.
- **Settings/SettingsHelper coupling**: MainframeMate reads password method from global settings JSON. Adyton uses port injection — the method is determined by which adapter is provided.
- **Global singleton pattern** (`CredentialStore` static methods): Replaced by injectable `CredentialProvider` / `DelegatedAccessProvider` instances.
- **German UI messages**: Removed from exception types.

### Raw credential exposure in MainframeMate

The following MainframeMate patterns expose raw credentials:
1. `Credentials.getPassword()` returns plaintext `String`
2. `CredentialStore.resolve()` returns `String[]{user, password}`
3. `SessionCipher` encrypts passwords in memory but decrypts on every access

In Corenth, these are replaced by:
1. `CredentialRef` is vault-internal and has no password getter — only an opaque `SecretRef`
2. `DelegatedAccessProvider.request()` returns a scoped `CredentialLease` — no raw material
3. `SessionCredentialCache` stores lease references only — no encrypted passwords
4. Normal modules never handle `SecretRef` or `CredentialRef`

### API layering

```
Normal modules  →  DelegatedAccessProvider.request()  →  CredentialLease (scoped grant)
                   DelegatedAccessProvider.authenticate()  →  DelegatedAccessResult

Vault internals →  CredentialProvider.acquire()  →  CredentialLease
                   SecretRef / CredentialRef  →  adapter-level identifiers
```

### Future adapter work (open issues)

- [ ] KeePass adapter module implementing `CredentialProvider` via KeePassRPC or PowerShell
- [ ] AES file-key adapter for portable persistent encryption
- [ ] DPAPI adapter for Windows environments
- [ ] Interactive credential prompt adapter (UI module, not in adyton)
