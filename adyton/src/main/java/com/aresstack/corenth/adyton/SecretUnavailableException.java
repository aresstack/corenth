package com.aresstack.corenth.adyton;

/**
 * Thrown when a requested secret or credential cannot be provided.
 * <p>
 * This exception is deliberately vague about the reason: it does not reveal
 * whether the secret exists but is inaccessible, or does not exist at all.
 * Callers should handle this uniformly as "access denied or unavailable".
 * <p>
 * Extends {@link AccessException} so broker call sites can catch either the
 * broad {@code AccessException} or the specific {@code SecretUnavailableException}.
 * <p>
 * <b>Migration note:</b> Unifies the exception taxonomy from MainframeMate:
 * <ul>
 *   <li>{@code AuthCancelledException} — user cancelled interactive auth</li>
 *   <li>{@code KeePassNotAvailableException} — KeePass misconfigured or unreachable</li>
 *   <li>{@code JnaBlockedException} — DPAPI native library blocked</li>
 *   <li>{@code PowerShellBlockedException} — PowerShell execution blocked</li>
 * </ul>
 * All of these map to "secret unavailable" in Corenth — the caller does not
 * need to know whether the failure was due to user cancellation, backend
 * misconfiguration, or OS security policy.
 */
public class SecretUnavailableException extends AccessException {

    public SecretUnavailableException(String message) {
        super(message);
    }

    public SecretUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
