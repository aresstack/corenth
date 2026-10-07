package com.aresstack.corenth.proasteion.platform.security.prompt;

/**
 * Immutable description of an interactive secret request, shown to the person being asked.
 * <p>
 * It deliberately carries only plain text: target system, principal, purpose, scope and the
 * authentication method name. It never carries secret references, secret material or adyton
 * types, so a UI adapter can render it without crossing the vault boundary.
 */
public final class SecretPromptRequest {

    private final String targetSystem;
    private final String principal;
    private final String purpose;
    private final String scope;
    private final String authenticationMethod;

    /**
     * Creates an explicit prompt request; every field is required.
     *
     * @param targetSystem         the system the secret unlocks
     * @param principal            the suggested principal (user name)
     * @param purpose              why the secret is needed
     * @param scope                which operation the secret is needed for
     * @param authenticationMethod the authentication method name, e.g. {@code ftp-password}
     */
    public SecretPromptRequest(String targetSystem, String principal, String purpose,
                               String scope, String authenticationMethod) {
        this.targetSystem = requireText(targetSystem, "targetSystem");
        this.principal = requireText(principal, "principal");
        this.purpose = requireText(purpose, "purpose");
        this.scope = requireText(scope, "scope");
        this.authenticationMethod = requireText(authenticationMethod, "authenticationMethod");
    }

    public String targetSystem() {
        return targetSystem;
    }

    public String principal() {
        return principal;
    }

    public String purpose() {
        return purpose;
    }

    public String scope() {
        return scope;
    }

    public String authenticationMethod() {
        return authenticationMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SecretPromptRequest)) return false;
        SecretPromptRequest that = (SecretPromptRequest) o;
        return targetSystem.equals(that.targetSystem)
                && principal.equals(that.principal)
                && purpose.equals(that.purpose)
                && scope.equals(that.scope)
                && authenticationMethod.equals(that.authenticationMethod);
    }

    @Override
    public int hashCode() {
        int result = targetSystem.hashCode();
        result = 31 * result + principal.hashCode();
        result = 31 * result + purpose.hashCode();
        result = 31 * result + scope.hashCode();
        result = 31 * result + authenticationMethod.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "SecretPromptRequest{target='" + targetSystem + "', principal='" + principal
                + "', purpose='" + purpose + "', scope='" + scope + "', method=" + authenticationMethod + "}";
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be null or empty");
        }
        return value;
    }
}
