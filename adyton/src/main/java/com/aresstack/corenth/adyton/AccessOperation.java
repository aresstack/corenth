package com.aresstack.corenth.adyton;

/**
 * The operation callback that the broker scopes around an {@link AccessHandle}.
 * <p>
 * Used with {@link AccessBroker#withAccess} to execute an operation within the
 * lifecycle of an access handle. The broker opens the handle, passes it to the
 * operation, and closes it when the operation completes (or fails).
 * <p>
 * This is the safe default pattern — the broker owns the handle lifecycle.
 *
 * @param <H> the specific handle type (e.g., FTP session, Wiki client)
 * @param <R> the operation result type
 *
 * @see AccessBroker#withAccess
 */
public interface AccessOperation<H extends AccessHandle, R> {

    /**
     * Executes the operation using the provided access handle.
     *
     * @param handle the authenticated access handle
     * @return the operation result
     * @throws Exception if the operation fails
     */
    R execute(H handle) throws Exception;
}
