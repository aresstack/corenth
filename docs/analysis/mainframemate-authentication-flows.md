# MainframeMate Authentication Flows — Analysis for `adyton`

Status: analysis only — no production code changes.
Scope: grounded inspection of the `research/` MainframeMate code as input for
PR aresstack/corenth#14 (`adyton: migrate credential boundary from MainframeMate
research`) and the broader `adyton` access-broker model. Related: issue
aresstack/corenth#1.

This document follows the structure requested in the parent issue:

1. Source inventory.
2. Table of authentication flows.
3. Table of current raw credential exposure points.
4. Recommended `adyton` public API vs internal SPI.
5. Recommendation for RAM cache semantics.
6. Required changes to PR aresstack/corenth#14 before merge.
7. Work deferred to later adapter issues.

The terminology used here distinguishes five concepts that the issue asked us to
keep separate:

```
Secret source            — KeePass, settings.json, interactive prompt, Windows-MY store
Secret cache             — RAM-only session cache (SessionCipher / sessionCache)
Authentication strategy  — protocol-specific login procedure (FTP login, MediaWiki token, Basic Auth, mTLS, JES login)
Access broker            — boundary component that brokers a strategy with a secret source under a scoped request
Access handle / grant    — what the rest of the application actually receives (an FTPClient, an HttpClient, a SSLContext, a cookie/session, a lease id)
```

The MainframeMate research code only partially has these concepts. The analysis
below shows where they exist, where they collapse together, and how `adyton`
should split them.

---

## 1. Source inventory

All paths are relative to the repository root.

### 1.1 Core credential boundary (already a small abstraction)

| File | Role |
| --- | --- |
| `research/core/src/main/java/de/bund/zrb/files/auth/CredentialsProvider.java` | SPI returning `Optional<Credentials>` for a `ConnectionId`. |
| `research/core/src/main/java/de/bund/zrb/files/auth/Credentials.java` | Mutable-looking value object with `getHost/getUsername/getPassword`. |
| `research/core/src/main/java/de/bund/zrb/files/auth/ConnectionId.java` | `(scheme, host, username)` key. `toString` returns `scheme://user@host`. |
| `research/core/src/main/java/de/bund/zrb/files/auth/AuthCancelledException.java` | Unchecked, indicates user-cancelled prompt; German message. |

### 1.2 App-level credential storage / resolution

| File | Role |
| --- | --- |
| `research/app/src/main/java/de/bund/zrb/util/CredentialStore.java` | Central façade: settings.json encrypted store + RAM `sessionCache` + KeePass dispatch + `String[]{user,password}` return type. |
| `research/app/src/main/java/de/bund/zrb/util/SessionCipher.java` | Pure-Java AES-256-GCM with a session-random key. Used only by the RAM cache. |
| `research/app/src/main/java/de/bund/zrb/util/WindowsCryptoUtil.java` | Strategy-pattern façade selecting `DPAPI` / `PowerShell DPAPI` / `JAVA_AES` / `KEEPASS` based on `Settings.passwordMethod`. |
| `research/app/src/main/java/de/bund/zrb/util/KeePassProvider.java` | KeePass façade dispatching to RPC vs PowerShell+KeePass.exe. Touches Swing for prompt dialogs. |
| `research/app/src/main/java/de/bund/zrb/util/KeePassRpcClient.java` | KeePassRPC pairing + AES key-exchange + JSON-RPC client (large, browser-extension protocol). |
| `research/app/src/main/java/de/bund/zrb/util/WindowsCryptoUtil.java` | (also listed above) DPAPI / AES / KEEPASS dispatcher. |
| `research/app/src/main/java/de/bund/zrb/util/AesCryptoProvider.java` | Portable file-key AES provider (uses an on-disk key). |
| `research/app/src/main/java/de/bund/zrb/util/DpapiCryptoProvider.java` | Windows DPAPI via JNA. |
| `research/app/src/main/java/de/bund/zrb/util/PowerShellCryptoProvider.java` | Windows DPAPI via PowerShell process. |
| `research/app/src/main/java/de/bund/zrb/files/impl/auth/InteractiveCredentialsProvider.java` | `CredentialsProvider` that delegates to a UI-aware password lookup; throws `AuthCancelledException`. |
| `research/app/src/main/java/de/bund/zrb/files/impl/auth/LoginManagerCredentialsProvider.java` | Non-interactive `CredentialsProvider` — cache-only. |
| `research/app/src/main/java/de/bund/zrb/login/LoginManager.java` | Singleton with its own RAM `sessionPasswordCache` and retry/block decisions. |
| `research/app/src/main/java/de/bund/zrb/login/LoginCredentials.java` | Another `(host, user, password)` value object — duplicates `Credentials`. |
| `research/app/src/main/java/de/bund/zrb/login/LoginCredentialsProvider.java` | Yet another credential provider abstraction at app level. |

### 1.3 Protocol connectors (consumers)

| File | Connector | How auth is wired |
| --- | --- | --- |
| `research/app/src/main/java/de/bund/zrb/files/impl/ftp/CommonsNetFtpFileService.java` | FTP / MVS over FTP | Constructor accepts either `(host,user,password)` or `(CredentialsProvider, ConnectionId)`. Calls `credentialsProvider.resolve(connectionId).getPassword()` and passes raw values to `ftpClient.login(user, password)`. |
| `research/app/src/main/java/de/bund/zrb/files/impl/ftp/jes/JesFtpJobSubmitter.java` | JES (JCL over FTP) | Same pattern as FTP: `credentialsProvider.resolve(connectionId)` then `ftp.login(credentials.getUsername(), credentials.getPassword())`. |
| `research/app/src/main/java/de/bund/zrb/ui/commands/ConnectNdvMenuCommand.java` | NDV (Software AG Natural Development Server) | Reads `Settings.user`, calls `LoginManager.getPassword(host,user)`, then `NdvService.connect(host, port, user, password)`. The NDV service stores the credentials inside `NdvSessionContext` and re-uses them for each library/file PAL request (`PalTypeSystemFile`, `PalTypeConnect`, `PalTypeLibId`). |
| `research/wiki-integration/src/main/java/de/bund/zrb/wiki/infrastructure/JwbfBotProvider.java` | MediaWiki (Wiki) | Calls `bot.login(credentials.username(), new String(credentials.password()))`. JWBF internally performs the MediaWiki two-step token login and keeps a cookie/session on the bot. The `bot` instance is cached per `site+user` and re-used. |
| `research/app/src/main/java/de/bund/zrb/confluence/ConfluenceRestClient.java` | Confluence Data Center | Constructs `Authorization: Basic <base64(user:password)>` once in the constructor and stores it as `authorizationHeaderValue`. Optionally builds an `SSLContext` from a `Windows-MY` certificate alias for mTLS. |
| `research/app/src/main/java/de/bund/zrb/confluence/ConfluenceConnectionConfig.java` | Confluence config carrier | Holds raw `username`, `password`, `clientCertificateAlias` — a `Credentials`-shaped object. |
| `research/app/src/main/java/de/bund/zrb/sharepoint/SharePointAuthenticator.java` | SharePoint UNC | Tries SSO (Kerberos/NTLM, no password), then stored credentials, then a Swing dialog; falls back to `net use` with raw user+password. |

### 1.4 Tests illustrating the contract

- `research/app/src/test/java/de/bund/zrb/files/impl/auth/LoginManagerCredentialsProviderTest.java`
- `research/app/src/test/java/de/bund/zrb/files/impl/ftp/vfs/VfsFtpSpikeTest.java`

---

## 2. Authentication flows in the research code

| Flow | Source files | Secret source | Auth method | Runtime access object | Current risks | Corenth recommendation |
| --- | --- | --- | --- | --- | --- | --- |
| FTP direct username/password | `CommonsNetFtpFileService` | `CredentialsProvider` → `Credentials.getPassword()` (KeePass / settings / prompt) | Plain `FTPClient.login(user, password)` | `org.apache.commons.net.ftp.FTPClient` (long-lived control connection) | Raw password crosses module boundary; same provider is used for read and for store; cancellation is an unchecked exception | Strategy must consume `SecretMaterial` inside adyton boundary; expose an `AccessHandle` wrapping the `FTPClient`, not the password. |
| JES JCL submission over FTP | `JesFtpJobSubmitter` | same as FTP | Plain FTP login; JCL submitted via SITE FILETYPE=JES | `FTPClient` per submit (short-lived) | Constructs a new connection per call → high pressure on KeePass + prompt; raw password recovered via `Credentials.getPassword()` | Reuse FTP `AccessHandle` from the FTP strategy; do not let JES call the credential layer directly. |
| NDV direct username/password | `ConnectNdvMenuCommand`, `NdvSessionContext`, `PalTypeConnect`, `PalTypeLibId`, `PalTypeSystemFile` | `LoginManager.getPassword(host, user)` (which goes through `CredentialStore` / KeePass / prompt) | `NdvService.connect(host, port, user, password)` then `sysFile.getPassword()` re-used per library | NDV session object (`NdvSessionContext`) carrying user+password fields | The protocol re-uses the raw password per call; `sysFile.getPassword()` exposes the password on every PAL request | NDV strategy must own the raw secret inside adyton and expose only a session handle; downstream code calls handle methods, never reads `getPassword()`. |
| MediaWiki two-step token login → cookie session | `JwbfBotProvider`, `JwbfWikiContentService`, `WikiSearchProvider`, `WikiSourceScanner` | `WikiCredentials` populated from settings/KeePass | JWBF `bot.login(user, pw)`: GET `login token`, POST `action=login`, store cookie | `MediaWikiBot` (carries cookie/session) cached per `site|user` | Password is decoded with `new String(credentials.password())` and passed to JWBF; `MediaWikiBot` is cached but the originating password is also kept in memory | This is the canonical example for an `AccessHandle`. Strategy authenticates once with a `SecretMaterial`, returns an `AccessHandle` wrapping the bot/cookie. Adyton then never needs the password again. |
| Confluence Basic Auth header | `ConfluenceRestClient`, `ConfluenceConnectionConfig`, `ConfluenceSearchProvider` | `ConfluenceConnectionConfig` (raw fields from settings) | `Authorization: Basic <base64(user:password)>` built once and re-used | The `authorizationHeaderValue` string + `HttpURLConnection` setup | The config object carries raw `getPassword()`; the header is a derived secret but the upstream config still leaks the password | Strategy should derive the header inside adyton, expose only an `HttpAuthHandle` (or pre-configured `HttpClient`/`HttpURLConnection` factory). Drop password getters from any module-visible config. |
| Confluence mTLS via `Windows-MY` | `ConfluenceRestClient.createSslContext` | Windows certificate store (alias selected in settings) | `KeyStore.getInstance("Windows-MY")` filtered to a single alias, used to build a `KeyManager[]` | `SSLContext` (then `HttpsURLConnection.setSSLSocketFactory`) | Private key never leaves Windows-MY — already a good "derived handle" model; only weakness is that it is built ad-hoc inside the REST client instead of through a strategy | This is the second canonical `AccessHandle` pattern. Codify it as a `CertificateBackedAccessHandle` and reuse for any other mTLS connector. |
| SharePoint UNC | `SharePointAuthenticator` | SSO (no secret) → stored creds (`CredentialStore`) → interactive dialog | `net use \\host\share /user:user password` subprocess | Mounted UNC path (kernel-level; cannot be returned as a Java handle) | Password is passed on the `net use` command line; if SSO works there is no secret at all | Model as a three-tier strategy: try SSO handle → try cached → fall back to interactive. Expose an `AccessHandle` that wraps "mounted UNC is available" rather than the secret. |
| KeePass lookup via KeePassRPC | `KeePassProvider.rpc*`, `KeePassRpcClient` | KeePass database via the KeePassRPC browser-extension protocol | AES key-exchange pairing then JSON-RPC `RetrieveLogins` etc. | `String[]{user, password}` returned from `rpcGetCredentialsByTitle` | The RPC client returns raw user/password to `CredentialStore`; the pairing dialog is Swing-coupled | Wrap KeePassRPC as a `CredentialProvider` (the adapter SPI in PR #14). Returns a `CredentialLease` / `SecretRef`, never raw `String`. UI pairing belongs to a separate adapter module. |
| KeePass lookup via PowerShell + `KeePass.exe` | `KeePassProvider.ps*` | KeePass `.kdbx` opened via Windows-account composite key, queried with embedded PowerShell | PowerShell subprocess returns user / password lines | `String[]{user, password}` | Subprocess output may end up in PowerShell history / process listings; subprocess is slow → fuels the RAM cache | Same as RPC: wrap as `CredentialProvider`. Mark the implementation as Windows-only. |
| RAM-only session credential cache | `CredentialStore.sessionCache` + `SessionCipher`; `LoginManager.sessionPasswordCache` | Filled by `CredentialStore.storeInSession` after the first prompt or KeePass hit | None — pure cache | SessionCipher-encrypted strings keyed by `"pwd:<entry>"` or `host\|user` | Two independent caches (`CredentialStore.sessionCache` and `LoginManager.sessionPasswordCache`) with different keys, no TTL, no idle timeout; cleared only on explicit `clearSessionCache()` / `LoginManager.clearSession()`. | Single `SecretMaterialCache` inside adyton with TTL + idle timeout + clear-on-shutdown + explicit `revoke(lease)`. No public `getPassword`. |
| Encrypted persistent credential storage | `CredentialStore` + `WindowsCryptoUtil` (DPAPI / PowerShell DPAPI / AES) | `settings.json:componentCredentials` ciphertext | Symmetric decrypt at read time | `String[]{user, password}` | All four backends collapse onto the same `String[]` exit point; key material varies wildly (DPAPI per-user vs portable AES file key) | Persistent storage is itself a `CredentialProvider` adapter; secrets exit only as `SecretRef`. |
| Interactive credential prompt | `InteractiveCredentialsProvider` + `LoginManager` Swing dialogs | User keystrokes | UI dialog | `Credentials(host, user, password)` | UI is mixed into the credential SPI; cancellation uses an unchecked exception | UI prompt must be a separate `CredentialProvider` adapter in the UI module. Cancellation must be a checked `AuthCancelledException` (PR #14 already does this — keep it). |

---

## 3. Where raw credentials are exposed today

| Exposure point | File | Form | Notes |
| --- | --- | --- | --- |
| `Credentials.getPassword()` | `research/core/.../files/auth/Credentials.java:23` | `String` | Public getter on a module-visible class. Direct contradiction of the adyton goal. |
| `Credentials.getUsername()` | same | `String` | Username is less sensitive but still leaks principal scope. |
| `CredentialStore.resolve(componentKey)` | `research/app/.../util/CredentialStore.java:58` | `String[]{user, password}` | Most-used exit point; called from connector code, search providers, settings UIs. |
| `CredentialStore.resolveIncludingEmpty(componentKey)` | same:109 | `String[]{user, password}` | Same shape, never returns null. |
| `KeePassProvider.rpcGetCredentialsByTitle(...)` / `psGetCredentialsByTitle(...)` | `research/app/.../util/KeePassProvider.java` | `String[]{user, password}` | Raw values returned from a subprocess / RPC. |
| `KeePassProvider.getPassword()` / `getUserName()` | same | `String` | Direct password retrieval used by `WindowsCryptoUtil.decrypt` when method is `KEEPASS`. |
| `WindowsCryptoUtil.decrypt(...)` | `research/app/.../util/WindowsCryptoUtil.java:81` | `String` plaintext | Generic decrypt point shared by all four backends. |
| `ftpClient.login(user, password)` | `research/app/.../files/impl/ftp/CommonsNetFtpFileService.java:137` | direct call | Raw password reaches commons-net. |
| `ftp.login(credentials.getUsername(), credentials.getPassword())` | `research/app/.../files/impl/ftp/jes/JesFtpJobSubmitter.java:103` | direct call | Same pattern in JES. |
| `NdvService.connect(host, port, user, password)` | `research/app/.../ui/commands/ConnectNdvMenuCommand.java:85` | direct call | Then `password` is retained inside `NdvSessionContext`. |
| `sysFile.getPassword()` | `research/ndv/.../transaction/impl/services/NdvSessionContext.java:375..389` | password re-read per request | Inside NDV transaction services. |
| `bot.login(credentials.username(), new String(credentials.password()))` | `research/wiki-integration/.../infrastructure/JwbfBotProvider.java:33` | `String` materialized from a `char[]` | Wiki-side. |
| `Authorization` header construction | `research/app/.../confluence/ConfluenceRestClient.java:440..444` | `Basic <base64(user:password)>` | Derived secret, but caller still passes plaintext. |
| `ConfluenceConnectionConfig.getPassword()` | `research/app/.../confluence/ConfluenceConnectionConfig.java:43` | `String` | Mirrors `Credentials.getPassword()` for Confluence. |
| `LoginCredentials.getPassword()` | `research/app/.../login/LoginCredentials.java` | `String` | Duplicates `Credentials`. |
| `LoginManager.getPassword(host, user)` | `research/app/.../login/LoginManager.java` | `String` | Singleton entry point used by `ConnectNdvMenuCommand`. |
| `netUse(uncPath, user, password)` | `research/app/.../sharepoint/SharePointAuthenticator.java:108..138` | command line | Password ends up in subprocess argv. |
| `new String(passField.getPassword())` | `research/app/.../sharepoint/SharePointAuthenticator.java:205` | `String` from JPasswordField | Standard Swing anti-pattern. |

The pattern is consistent: every connector eventually calls `String getPassword()` and then passes it to a third-party API. That is exactly the boundary adyton must replace.

---

## 4. Which secrets can become derived `AccessHandle`s, and which cannot

### 4.1 Can become a handle (password is needed once, then thrown away)

| Connector | Handle |
| --- | --- |
| MediaWiki | `WikiAccessHandle` wrapping the authenticated `MediaWikiBot` (cookie/session lives inside JWBF). |
| Confluence Basic Auth | `HttpAuthHandle` exposing the prepared `Authorization` header value (the password is salted-out at handle build time). |
| Confluence mTLS (Windows-MY) | `CertificateBackedAccessHandle` exposing the `SSLContext` / `SSLSocketFactory` (private key never leaves Windows-MY). |
| KeePass session for the main password | already implicitly an `AccessHandle` via Windows account composite key — no secret material to cache. |
| SharePoint via SSO | `KerberosAccessHandle` — no secret material at all. |

### 4.2 Still requires raw secret material at connection time

| Connector | Why |
| --- | --- |
| FTP (`CommonsNetFtpFileService`) | `ftpClient.login(user, password)` is the only API; the password is needed once per control connection. |
| JES over FTP (`JesFtpJobSubmitter`) | Same, but per submit, so the handle must be a *reused* FTP connection. |
| NDV (`NdvService`) | `NdvService.connect(...)` plus per-PAL `sysFile.getPassword()` — protocol re-validates the password on each library request. |
| SharePoint fallback (`net use`) | Raw `user`/`password` on the subprocess command line; no API alternative. |

For these, the access broker must accept a controlled callback (an
`AuthenticationStrategy<H>` that internally pulls a `SecretMaterial` from adyton
and immediately drops it) rather than exposing a password getter to general
modules.

---

## 5. Recommended RAM cache semantics for adyton

The MainframeMate code already proved two things that adyton must preserve:

1. The first call to KeePass (RPC or PowerShell) is slow; users complain if it
   runs on every operation. A RAM cache is required for usability.
2. The cache must never reach disk (`SessionCipher` uses a per-JVM random key,
   on purpose).

But MainframeMate's cache has two structural defects that adyton must fix:

1. There are *two* caches (`CredentialStore.sessionCache` and
   `LoginManager.sessionPasswordCache`) with different keys and lifecycles.
2. The cache stores raw `user|password` strings, which then exit as `String[]`.

Recommended adyton cache contract:

| Property | Recommended value |
| --- | --- |
| What is cached | (a) `SecretMaterial` (vault-internal) keyed by `CredentialRequest` identity; (b) derived `AccessHandle` keyed by the same when the strategy supports caching (e.g. Wiki cookie, Confluence header, SSLContext). |
| What is NOT cached | The `Credentials`-shaped `(user, password)` pair must not be a public cache entry type at all. |
| Cache key | A normalized form of `CredentialRequest{ target, principal, purpose, scope }`. Two requests with the same `(target, principal, purpose, scope)` may share. Requests with different `purpose` or `scope` must not share. |
| TTL | Absolute `expiresAt` derived from `CredentialRequest.requestedTtlMillis` (already in PR #14). Default cap (e.g. 60 minutes) enforced even if the requester asks for longer. |
| Idle timeout | Independent sliding timeout (e.g. 10 minutes since last use). Forces re-auth on long-idle sessions. |
| Clear on shutdown | Mandatory. Hook through a JVM shutdown hook *and* an explicit `close()` on the broker. |
| Explicit clear / revoke | `revoke(lease)` (already in PR #14) and `revokeAll(target)` for failure paths (FTP `530` → drop all FTP-target leases for that user). |
| Encryption at rest in RAM | Use the existing `SessionCipher` AES-256-GCM with a per-JVM key for any raw `SecretMaterial`. Derived `AccessHandle`s do not need re-encryption since they no longer contain the password. |
| Negative caching | Cache `AuthCancelledException` for a short window (a few seconds) so a cancelled prompt does not immediately re-pop on the next click. MainframeMate does this implicitly via `LoginManager.loginTemporarilyBlocked` — formalize it. |

---

## 6. Validation of the proposed `AccessBroker` / `AuthenticationStrategy` model

The model proposed in the issue is:

```java
interface AccessBroker {
    <H extends AccessHandle, R> R withAccess(
        AccessRequest request,
        AuthenticationStrategy<H> strategy,
        AccessOperation<H, R> operation) throws AccessException;
}

interface AuthenticationStrategy<H extends AccessHandle> {
    boolean supports(AuthenticationMethod method);
    H authenticate(AccessRequest request, SecretMaterial material) throws AccessException;
}

interface AccessOperation<H extends AccessHandle, R> {
    R execute(H handle) throws Exception;
}

interface AccessHandle extends AutoCloseable {
    AccessGrant grant();
    @Override void close();
}
```

This model is **suitable** for the MainframeMate flows above, with three
clarifications grounded in the research code:

1. **`AccessHandle` lifetimes vary widely.** FTP and Wiki handles are *long-lived*
   sessions; Confluence Basic Auth is *connectionless* (the handle is effectively
   a function `HttpRequest -> HttpRequest` setting a header); the mTLS handle is
   *factory-shaped* (`SSLContext` reused across many requests). The interface is
   wide enough to cover all three, but the documentation must show all three
   patterns or implementers will collapse them.
2. **`withAccess(...)` should not be the only entry point.** Wiki and NDV both
   benefit from a pre-acquired long-lived handle that the caller uses many times
   (search-as-you-type, repeated PAL calls). A second method
   `acquire(request, strategy) -> AccessHandle` is needed; `withAccess` is the
   safe default that closes the handle deterministically.
3. **`SecretMaterial` must be a sealed boundary type.** The research code's
   mistake is that the boundary type (`Credentials`) had a public
   `getPassword()`. Adyton's `SecretMaterial` should be package-private to the
   `adyton.strategy` SPI module, mirror PR #14's `SecretRef` design, and never
   appear on `AccessHandle`, `AccessGrant`, `CredentialLease` or any module-facing
   type.

The model in PR #14 (`DelegatedAccessProvider` + `CredentialLease` +
`CredentialProvider` SPI) is consistent with this and can be kept; what is
missing is the `AuthenticationStrategy<H>` and `AccessHandle<H>` pair that
turns a lease into a protocol-specific handle. See §7.

---

## 7. Required changes to PR aresstack/corenth#14 before merge

### 7.1 Define the missing boundary types

PR #14 currently introduces `DelegatedAccessProvider`, `CredentialRequest`,
`CredentialLease`, `DelegatedAccessResult`, `CredentialProvider` (adapter SPI),
`SecretRef`, `CredentialRef`, `SessionCredentialCache`,
`SecretUnavailableException`, `AuthCancelledException`. That is the secret /
lease / cache spine. It does **not** yet have:

- `AccessHandle` — the per-protocol handle the rest of the application actually
  uses (FTPClient wrapper, MediaWikiBot wrapper, `Authorization` header,
  `SSLContext`).
- `AccessOperation<H,R>` — the operation callback the broker scopes around the
  handle.
- `AuthenticationStrategy<H>` — the SPI that turns a lease + `SecretMaterial`
  into an `AccessHandle<H>`.
- `AuthenticationMethod` — the discriminator (`FTP_PASSWORD`,
  `NDV_PASSWORD`, `MEDIA_WIKI_LOGIN`, `HTTP_BASIC`, `MTLS_WINDOWS_MY`,
  `SMB_NET_USE`, `SSO`).
- `SecretCachePolicy` — a small value type so callers (and PR #14's
  `SessionCredentialCache`) can express the TTL / idle / negative-caching
  semantics from §5 without each adapter inventing them.
- `SecretMaterial` — the package-internal sealed type the strategies consume
  (kept invisible to normal modules; see §6.3).

These can be empty interfaces / records, but they must be in place before any
adapter PR begins, otherwise adapters will reach back into `SecretRef`
directly and the boundary will leak.

### 7.2 Confirm the contracts in the cache

`SessionCredentialCache` in PR #14 is described as "lease-based, auto-expires".
Before merge, confirm that:

- It enforces an absolute TTL cap independent of `requestedTtlMillis`.
- It supports an idle timeout (or explicitly documents that it does not, with a
  follow-up issue tracked).
- `revoke(lease)` is the only public removal path.
- It clears on JVM shutdown.

These are the points where MainframeMate's two-cache split caused real bugs.

### 7.3 Remove residual raw-password surface

Audit PR #14's types for any of the following — they must not exist on the
module-facing API:

- `getPassword(): String`
- `password(): char[]`
- `getCredentials(): String[]`
- `Authorization` header value getters on `DelegatedAccessResult` (the issue
  description says `toString()` masks; verify all getters do too).
- Logging of `CredentialLease.toString()` that includes principal / target in
  exception messages can be acceptable, but the lease *id* must remain opaque.

### 7.4 Keep `AuthCancelledException` checked

PR #14 says the exception is now checked, subclass of
`SecretUnavailableException`. Confirm that the public broker method signature
declares it (not `throws Exception`), so call sites must distinguish
cancellation from technical failure (the research code conflated these because
the exception was unchecked).

### 7.5 Drop UI / Settings coupling

PR #14 says it does not copy `KeePassRpcPairingDialog` or
`SettingsHelper` coupling. Verify that no transitive import remains
(`javax.swing.*`, `de.bund.zrb.helper.SettingsHelper`,
`de.bund.zrb.model.Settings`). The research code mixes these freely; the
adyton module must compile with neither Swing nor `Settings` on its classpath.

### 7.6 Documentation

Add to the adyton README (or link to this analysis from it):

- The five-concept table from the top of this document.
- The two handle patterns from §4 (long-lived session vs. derived material).
- The note that `SecretMaterial` is internal and `getPassword()` is forbidden.

---

## 8. Work explicitly deferred to later adapter issues

PR #14 should **not** attempt the following — each is a separate issue:

| Deferred work | Why |
| --- | --- |
| Real KeePassRPC client | `research/.../KeePassRpcClient.java` is ~1000 lines including AES key-exchange, pairing dialog, JSON-RPC. Belongs in `adyton-keepass-rpc` adapter module. |
| Real PowerShell / `KeePass.exe` client | Windows-only, subprocess-heavy, requires its own integration tests. Belongs in `adyton-keepass-ps` adapter module. |
| DPAPI provider (JNA) | Platform-specific native binding. `adyton-dpapi-jna` adapter. |
| PowerShell DPAPI provider | Same, subprocess-based. `adyton-dpapi-ps` adapter. |
| AES file-key provider | Portable. `adyton-aes` adapter. |
| FTP `AuthenticationStrategy` + `FtpAccessHandle` | Lives next to the FTP connector module; needs commons-net on its classpath. |
| NDV `AuthenticationStrategy` + `NdvAccessHandle` | Lives next to NDV; must hide `sysFile.getPassword()` from any non-strategy code. |
| MediaWiki `AuthenticationStrategy` + `WikiAccessHandle` | Lives next to the wiki module; wraps `MediaWikiBot`. |
| Confluence Basic Auth strategy + `HttpAuthHandle` | Belongs in confluence module. |
| Confluence mTLS strategy + `CertificateBackedAccessHandle` | Same module; Windows-MY specifics stay there. |
| SharePoint SSO / `net use` strategy | Same. |
| Interactive prompt provider | UI module (Swing / future GUI), never in adyton. |
| Migration of `LoginManager` singleton | Behavior (retry decisions, temporary block) should be re-implemented on top of the broker as a separate UX concern. |

---

## 9. Summary

- The MainframeMate research code has a usable nucleus (`CredentialsProvider`,
  `ConnectionId`, `Credentials`, `SessionCipher`, `WindowsCryptoUtil` strategy
  selector) but ends every flow at a raw `String getPassword()` or
  `String[]{user, password}`, and that exit point is exactly what adyton must
  remove.
- Three protocols (Wiki, Confluence Basic, Confluence mTLS) already prove the
  derived-handle pattern works in practice; four protocols (FTP, JES, NDV,
  SharePoint fallback) genuinely need raw secret material at connect time and
  must be served by a controlled `AuthenticationStrategy` callback rather than a
  public password getter.
- The RAM cache must be unified (one cache, not two), keyed on
  `CredentialRequest`, with absolute TTL, idle timeout, shutdown hook, and
  explicit `revoke`.
- PR aresstack/corenth#14 has the right shape for the lease / provider spine but
  is missing the `AccessHandle` / `AuthenticationStrategy` / `AccessOperation` /
  `AuthenticationMethod` / `SecretCachePolicy` / `SecretMaterial` types. These
  should be added (as contracts only) before merge; all real adapters and
  protocol strategies are deferred to follow-up issues.
