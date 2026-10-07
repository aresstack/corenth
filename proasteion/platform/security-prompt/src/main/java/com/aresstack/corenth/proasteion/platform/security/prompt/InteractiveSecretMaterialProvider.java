package com.aresstack.corenth.proasteion.platform.security.prompt;

import com.aresstack.corenth.adyton.AccessRequest;
import com.aresstack.corenth.adyton.AuthCancelledException;
import com.aresstack.corenth.adyton.SecretMaterial;
import com.aresstack.corenth.adyton.SecretMaterialFactory;
import com.aresstack.corenth.adyton.SecretMaterialProvider;
import com.aresstack.corenth.adyton.SecretReleaseDeniedException;
import com.aresstack.corenth.adyton.SecretUnavailableException;

/**
 * Adyton {@link SecretMaterialProvider} that asks a person for the secret through a
 * {@link SecretPromptPort}.
 * <p>
 * Rules:
 * <ul>
 *   <li>The request must state purpose and scope; otherwise nobody is asked.</li>
 *   <li>The {@link InteractionGate} must permit interaction; otherwise nobody is asked. This keeps
 *       background operations from opening dialogs.</li>
 *   <li>Outcomes map to adyton exceptions: {@code CANCELLED} to {@link AuthCancelledException},
 *       {@code DENIED} to {@link SecretReleaseDeniedException}, {@code UNAVAILABLE} and every
 *       prompt failure to {@link SecretUnavailableException}. A secret-source chain stops on the
 *       first two and may fall through to another source only on the plain unavailable case.</li>
 *   <li>A supplied secret becomes short-lived {@link SecretMaterial} owned by the broker; the
 *       prompt result is wiped before this method returns.</li>
 * </ul>
 * The provider caches nothing. Remembering a cancellation to avoid re-prompting belongs to the
 * broker's cache policy, not here.
 */
public final class InteractiveSecretMaterialProvider implements SecretMaterialProvider {

    private final SecretPromptPort promptPort;
    private final InteractionGate interactionGate;

    public InteractiveSecretMaterialProvider(SecretPromptPort promptPort, InteractionGate interactionGate) {
        if (promptPort == null) {
            throw new IllegalArgumentException("Secret prompt port must not be null");
        }
        if (interactionGate == null) {
            throw new IllegalArgumentException("Interaction gate must not be null");
        }
        this.promptPort = promptPort;
        this.interactionGate = interactionGate;
    }

    @Override
    public SecretMaterial resolve(AccessRequest request) throws SecretUnavailableException {
        if (request == null) {
            throw new IllegalArgumentException("Access request must not be null");
        }
        SecretPromptRequest promptRequest = promptRequestFor(request);
        if (!interactionGate.permitsInteraction(promptRequest)) {
            throw new SecretUnavailableException("Interactive secret entry is not permitted in the current context");
        }
        SecretPromptResult result = ask(promptRequest);
        try {
            return materialFrom(request, result);
        } finally {
            result.close();
        }
    }

    @Override
    public void release(SecretMaterial material) {
        if (material != null) {
            material.close();
        }
    }

    private static SecretPromptRequest promptRequestFor(AccessRequest request) throws SecretUnavailableException {
        if (isBlank(request.purpose()) || isBlank(request.scope())) {
            throw new SecretUnavailableException("Interactive secret entry requires an explicit purpose and scope");
        }
        return new SecretPromptRequest(request.targetSystem(), request.principal(), request.purpose(),
                request.scope(), request.method().name());
    }

    private SecretPromptResult ask(SecretPromptRequest promptRequest) throws SecretUnavailableException {
        SecretPromptResult result;
        try {
            result = promptPort.prompt(promptRequest);
        } catch (RuntimeException e) {
            throw new SecretUnavailableException("Interactive secret prompt failed", e);
        }
        if (result == null) {
            throw new SecretUnavailableException("Interactive secret prompt returned no result");
        }
        return result;
    }

    private static SecretMaterial materialFrom(AccessRequest request, SecretPromptResult result)
            throws SecretUnavailableException {
        switch (result.outcome()) {
            case SUPPLIED:
                String principal = result.enteredPrincipal() == null ? request.principal() : result.enteredPrincipal();
                return SecretMaterialFactory.fromSecret(request.credentialRef(), principal, result.secretForTransfer());
            case CANCELLED:
                throw new AuthCancelledException("Interactive secret entry was cancelled");
            case DENIED:
                throw new SecretReleaseDeniedException("Interactive secret release was denied");
            case UNAVAILABLE:
            default:
                throw new SecretUnavailableException("Interactive secret entry is unavailable");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
