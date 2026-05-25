# MainframeMate → adyton Migration Inventory

This document records the inspection of MainframeMate credential/security source
files (under `research/`) and the migration decisions for the `adyton` module.

## Source files inspected

All files listed in the issue were reviewed from the `research/` directory.

## Migration inventory

| MainframeMate source file | Decision | Corenth target | Reason |
|---|---|---|---|
| `core/.../files/auth/CredentialsProvider.java` | adapt | `adyton:CredentialProvider` | Core port pattern preserved. Return type changed from `Optional<Credentials>` (raw password) to `CredentialLease` (opaque, time-limited). |
| `core/.../files/auth/ConnectionId.java` | adapt | `adyton:CredentialRequest` | Connection identity concept generalised into a request with targetSystem + principal + purpose. Scheme/host breakdown replaced by abstract target. |
| `core/.../files/auth/Credentials.java` | adapt | `adyton:CredentialRef` | Principal identity preserved; raw `getPassword()` removed, replaced by opaque `SecretRef`. |
| `core/.../files/auth/AuthCancelledException.java` | adapt | `adyton:SecretUnavailableException` | Unified with other failure modes. Corenth callers don't distinguish cancellation from unavailability. |
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
| `SecretRef` | new-corenth-api | Opaque secret handle concept. MainframeMate passes raw strings; Corenth needs a boundary type. |
| `CredentialLease` | new-corenth-api | Time-limited access grant. MainframeMate's session cache has no expiration; Corenth adds explicit lease lifetimes. |
| `DelegatedAccessProvider` | new-corenth-api | Operation-based access port. No direct MainframeMate equivalent, but KeePassRpcClient's authenticated operations are the closest precedent. |
| `DelegatedAccessResult` | new-corenth-api | Result type for delegated operations. |

## Decisions rationale

### Do-not-copy reasoning

- **KeePassRpcPairingDialog**: Swing dialog for SRP pairing PIN entry. Adyton must remain UI-free.
- **Settings/SettingsHelper coupling**: MainframeMate reads password method from global settings JSON. Adyton uses port injection — the method is determined by which adapter is provided.
- **Global singleton pattern** (`CredentialStore` static methods): Replaced by injectable `CredentialProvider` instances.
- **German UI messages**: Removed from exception types.

### Raw credential exposure in MainframeMate

The following MainframeMate patterns expose raw credentials:
1. `Credentials.getPassword()` returns plaintext `String`
2. `CredentialStore.resolve()` returns `String[]{user, password}`
3. `SessionCipher` encrypts passwords in memory but decrypts on every access

In Corenth, these are replaced by:
1. `CredentialRef` has no password getter — only an opaque `SecretRef`
2. `CredentialProvider.acquire()` returns a `CredentialLease` — no raw material
3. `SessionCredentialCache` stores lease references only — no encrypted passwords

### Future adapter work (open issues)

- [ ] KeePass adapter module implementing `CredentialProvider` via KeePassRPC or PowerShell
- [ ] AES file-key adapter for portable persistent encryption
- [ ] DPAPI adapter for Windows environments
- [ ] Interactive credential prompt adapter (UI module, not in adyton)
