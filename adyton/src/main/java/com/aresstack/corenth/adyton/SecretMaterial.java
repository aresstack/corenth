package com.aresstack.corenth.adyton;

/**
 * Vault-internal boundary type representing resolved secret material.
 * <p>
 * <b>This type is intended for {@link AuthenticationStrategy} implementations only.</b>
 * It must never appear on any module-facing API, connector interface, or public getter
 * exposed to normal modules ({@code holkas}, {@code deigma}, {@code tamias}, etc.).
 * <p>
 * Strategy implementations — which may reside in separate packages or modules —
 * access the material through the public interface methods below. This is safe because:
 * <ul>
 *   <li>Only trusted adapter code implements {@link AuthenticationStrategy}.</li>
 *   <li>The broker never passes {@code SecretMaterial} to connector/module code.</li>
 *   <li>Construction remains internal to adyton (package-private factory).</li>
 * </ul>
 * <p>
 * <b>Security properties:</b>
 * <ul>
 *   <li>{@code toString()} never reveals the material</li>
 *   <li>No method named {@code getPassword()} — deliberate API friction</li>
 *   <li>Implementations should support explicit wiping when possible</li>
 * </ul>
 * <p>
 * <b>Migration note:</b> In MainframeMate, {@code Credentials.getPassword()}
 * returns a raw {@code String}. In Corenth, raw secret material is encapsulated
 * here and never crosses the vault boundary to normal module code. The closest
 * MainframeMate equivalent is the decrypted output of {@code SessionCipher.decrypt()},
 * which also should not have been public — adyton formalizes that constraint.
 *
 * @see AuthenticationStrategy#authenticate(AccessRequest, SecretMaterial)
 * @see SecretMaterialCache
 */
public interface SecretMaterial {

    /**
     * Returns the principal/username associated with this material.
     * <p>
     * Available to strategy implementations for protocols that need an explicit
     * username (FTP USER, HTTP Basic, MediaWiki lgname, etc.).
     *
     * @return the principal identity, never {@code null}
     */
    String principal();

    /**
     * Returns the secret value (password, token, key material) as a char array.
     * <p>
     * Strategy implementations consume this to perform authentication. The array
     * should be wiped (zeroed) after use where the protocol allows it.
     * <p>
     * <b>Warning:</b> This method is for trusted {@link AuthenticationStrategy}
     * implementations only. It must never be called from normal module code.
     *
     * @return the secret material as a char array
     */
    char[] secret();

    /**
     * Returns the secret reference id used internally for cache keying.
     * <p>
     * This is an opaque identifier — not the actual secret value.
     *
     * @return the internal reference identifier
     */
    String secretRefId();
}
