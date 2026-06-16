package com.aresstack.corenth.proasteion.platform.network;

import java.net.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result of route planning for a single connection.
 */
public final class NetworkRoutePlan {

    private final URI targetUri;
    private final List<NetworkRouteStage> stages;

    public NetworkRoutePlan(URI targetUri, List<NetworkRouteStage> stages) {
        if (targetUri == null) {
            throw new IllegalArgumentException("Target URI must not be null");
        }
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("Route stages must not be null or empty");
        }
        this.targetUri = targetUri;
        this.stages = Collections.unmodifiableList(new ArrayList<NetworkRouteStage>(stages));
    }

    public URI targetUri() {
        return targetUri;
    }

    public List<NetworkRouteStage> stages() {
        return stages;
    }

    public boolean hasPlatformProxy() {
        return contains(NetworkRouteStageType.PLATFORM_PROXY);
    }

    public boolean hasSecureGateway() {
        return contains(NetworkRouteStageType.SECURE_GATEWAY);
    }

    public boolean isDirectOnly() {
        return stages.size() == 1 && stages.get(0).type() == NetworkRouteStageType.DIRECT;
    }

    /**
     * Returns the classic Java proxy used for the first network hop.
     * <p>
     * If the route starts directly or with a secure gateway without a platform
     * proxy, this returns {@link Proxy#NO_PROXY}.
     */
    public Proxy firstHopProxy() {
        for (NetworkRouteStage stage : stages) {
            if (stage.isPlatformProxy()) {
                return stage.proxy();
            }
        }
        return Proxy.NO_PROXY;
    }

    private boolean contains(NetworkRouteStageType type) {
        for (NetworkRouteStage stage : stages) {
            if (stage.type() == type) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkRoutePlan)) return false;
        NetworkRoutePlan that = (NetworkRoutePlan) o;
        return targetUri.equals(that.targetUri) && stages.equals(that.stages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetUri, stages);
    }

    @Override
    public String toString() {
        return "NetworkRoutePlan{targetUri=" + targetUri + ", stages=" + stages + "}";
    }
}
