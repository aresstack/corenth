package com.aresstack.corenth.proasteion.platform.security.prompt;

import java.util.Arrays;

/**
 * Outcome of one interactive secret prompt.
 * <p>
 * A supplied secret is held in a private copy that {@link #close()} wipes. The result is single
 * use: the consumer reads it once and closes it. Its {@code toString()} never reveals the secret.
 */
public final class SecretPromptResult implements AutoCloseable {

    /** What the person did with the prompt. */
    public enum Outcome {
        /** The person entered the secret. */
        SUPPLIED,
        /** The person explicitly refused to release the secret for this request. */
        DENIED,
        /** The person aborted the prompt without a decision. */
        CANCELLED,
        /** No interaction surface was available, for example a headless host. */
        UNAVAILABLE
    }

    private static final char[] NO_SECRET = new char[0];

    private final Outcome outcome;
    private final String principal;
    private final char[] secret;
    private volatile boolean closed;

    private SecretPromptResult(Outcome outcome, String principal, char[] secret) {
        this.outcome = outcome;
        this.principal = principal;
        this.secret = secret;
    }

    /**
     * Creates a result for an entered secret, keeping the principal from the request.
     * <p>
     * The secret is copied; the caller wipes its own array after this call.
     */
    public static SecretPromptResult supplied(char[] secret) {
        return supplied(null, secret);
    }

    /**
     * Creates a result for an entered secret with the principal the person confirmed or changed.
     * <p>
     * The secret is copied; the caller wipes its own array after this call.
     *
     * @param principal the entered principal, or {@code null} to keep the requested one
     * @param secret    the entered secret, not {@code null}
     */
    public static SecretPromptResult supplied(String principal, char[] secret) {
        if (secret == null) {
            throw new IllegalArgumentException("secret must not be null");
        }
        String effectivePrincipal = principal == null || principal.trim().isEmpty() ? null : principal;
        return new SecretPromptResult(Outcome.SUPPLIED, effectivePrincipal, Arrays.copyOf(secret, secret.length));
    }

    public static SecretPromptResult denied() {
        return new SecretPromptResult(Outcome.DENIED, null, NO_SECRET);
    }

    public static SecretPromptResult cancelled() {
        return new SecretPromptResult(Outcome.CANCELLED, null, NO_SECRET);
    }

    public static SecretPromptResult unavailable() {
        return new SecretPromptResult(Outcome.UNAVAILABLE, null, NO_SECRET);
    }

    public Outcome outcome() {
        return outcome;
    }

    /** Returns the principal entered by the person, or {@code null} to keep the requested one. */
    public String enteredPrincipal() {
        return principal;
    }

    /** Returns whether the result has been closed and its secret wiped. */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Returns the internal secret array without copying, for the provider in this package only.
     * The array is wiped by {@link #close()}.
     */
    char[] secretForTransfer() {
        return closed ? NO_SECRET : secret;
    }

    /** Wipes the secret. Further reads return an empty secret. */
    @Override
    public void close() {
        if (!closed) {
            Arrays.fill(secret, '\0');
            closed = true;
        }
    }

    @Override
    public String toString() {
        return "SecretPromptResult{outcome=" + outcome + ", secret=***}";
    }
}
