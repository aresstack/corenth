package com.aresstack.corenth.adyton;

/**
 * SPI for turning secret material into a protocol-specific {@link AccessHandle}.
 * <p>
 * Each protocol family (FTP, Wiki, Confluence, NDV, etc.) provides its own
 * strategy implementation. The strategy receives the resolved
 * {@link SecretMaterial} from the broker, performs the protocol-specific
 * authentication, and returns a handle that the connector can use.
 * <p>
 * <b>Important:</b> Strategy implementations are trusted code within the vault
 * boundary. They receive {@link SecretMaterial} but must never expose it to
 * callers. The returned {@link AccessHandle} must not provide any getter for
 * raw passwords or secret tokens.
 * <p>
 * <b>Migration note:</b> In MainframeMate, each connector directly calls
 * {@code CredentialStore.resolve()} to get a raw password, then performs its
 * own authentication. In Corenth, the strategy encapsulates the authentication
 * step so the connector never sees the raw secret — only the resulting handle.
 * <p>
 * Examples for later adapter work:
 * <ul>
 *   <li>{@code FtpAuthenticationStrategy} → creates an FTP client session</li>
 *   <li>{@code WikiAuthenticationStrategy} → performs MediaWiki login, returns cookie handle</li>
 *   <li>{@code HttpBasicAuthenticationStrategy} → creates Authorization header handle</li>
 *   <li>{@code MtlsAuthenticationStrategy} → creates SSLContext handle</li>
 * </ul>
 *
 * @param <H> the specific handle type produced by this strategy
 *
 * @see AccessBroker
 * @see SecretMaterial
 */
public interface AuthenticationStrategy<H extends AccessHandle> {

    /**
     * Returns whether this strategy supports the given authentication method.
     *
     * @param method the method to check
     * @return {@code true} if this strategy can handle the method
     */
    boolean supports(AuthenticationMethod method);

    /**
     * Authenticates using the given request and secret material, producing a handle.
     * <p>
     * Implementations must not store or expose the {@link SecretMaterial} beyond
     * what is needed for the authentication step. The material should be consumed
     * and forgotten; only the resulting handle persists.
     *
     * @param request  the access request (target, principal, scope, method)
     * @param material the resolved secret material (vault-internal)
     * @return an authenticated access handle
     * @throws AccessException if authentication fails
     */
    H authenticate(AccessRequest request, SecretMaterial material) throws AccessException;
}
