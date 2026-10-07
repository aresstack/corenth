package com.aresstack.corenth.proasteion.platform.security.prompt;

/**
 * Narrow interaction callback that asks a person for a secret.
 * <p>
 * This is a port, not a dialog: it carries no UI types. A UI adapter (for example a later Exedra
 * Swing implementation) or a test double implements it. The call is synchronous and blocking;
 * the implementation decides how it interacts with the person.
 * <p>
 * Implementations must not log, cache or retain the entered secret. The returned
 * {@link SecretPromptResult} is owned by the caller, which wipes it after use.
 */
public interface SecretPromptPort {

    /**
     * Asks for the secret described by the request.
     *
     * @param request what is requested, for which target, purpose and scope
     * @return the outcome of the interaction, never {@code null}
     */
    SecretPromptResult prompt(SecretPromptRequest request);
}
