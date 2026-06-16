package com.aresstack.corenth.proasteion.platform.network;

/**
 * Thrown when a network route cannot be planned safely.
 */
public class NetworkRoutingException extends Exception {

    public NetworkRoutingException(String message) {
        super(message);
    }

    public NetworkRoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}
