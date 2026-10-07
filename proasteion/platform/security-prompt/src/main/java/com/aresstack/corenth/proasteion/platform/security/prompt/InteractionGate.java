package com.aresstack.corenth.proasteion.platform.security.prompt;

/**
 * Decides whether a secret prompt may interact with a person right now.
 * <p>
 * The gate prevents silent dialogs from background operations: the
 * {@link InteractiveSecretMaterialProvider} asks the gate before it calls the
 * {@link SecretPromptPort} and fails with an unavailable secret when interaction is not permitted.
 */
public interface InteractionGate {

    /**
     * Returns whether the prompt for the given request may be shown in the current context.
     *
     * @param request the prompt that would be shown
     * @return {@code true} to allow the interaction
     */
    boolean permitsInteraction(SecretPromptRequest request);
}
