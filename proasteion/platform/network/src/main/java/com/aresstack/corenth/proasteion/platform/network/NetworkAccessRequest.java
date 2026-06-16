package com.aresstack.corenth.proasteion.platform.network;

import java.net.URI;
import java.util.Objects;

/**
 * A route-planning request for one concrete outbound network connection.
 */
public final class NetworkAccessRequest {

    private final URI targetUri;
    private final String operation;
    private final NetworkAccessPolicy policy;

    public NetworkAccessRequest(URI targetUri, String operation, NetworkAccessPolicy policy) {
        if (targetUri == null) {
            throw new IllegalArgumentException("Target URI must not be null");
        }
        if (policy == null) {
            throw new IllegalArgumentException("Network access policy must not be null");
        }
        this.targetUri = targetUri;
        this.operation = operation;
        this.policy = policy;
    }

    public URI targetUri() {
        return targetUri;
    }

    public String operation() {
        return operation;
    }

    public NetworkAccessPolicy policy() {
        return policy;
    }

    public String targetUrl() {
        return targetUri.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkAccessRequest)) return false;
        NetworkAccessRequest that = (NetworkAccessRequest) o;
        return targetUri.equals(that.targetUri)
                && Objects.equals(operation, that.operation)
                && policy.equals(that.policy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetUri, operation, policy);
    }

    @Override
    public String toString() {
        return "NetworkAccessRequest{targetUri=" + targetUri
                + ", operation='" + operation + '\''
                + ", policy=" + policy + "}";
    }
}
