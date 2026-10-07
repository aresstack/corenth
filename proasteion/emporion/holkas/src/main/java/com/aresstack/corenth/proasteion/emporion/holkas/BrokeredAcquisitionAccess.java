package com.aresstack.corenth.proasteion.emporion.holkas;

import com.aresstack.corenth.adyton.AccessBroker;
import com.aresstack.corenth.adyton.AccessException;
import com.aresstack.corenth.adyton.AccessHandle;
import com.aresstack.corenth.adyton.AccessRequest;
import com.aresstack.corenth.adyton.AuthCancelledException;
import com.aresstack.corenth.adyton.AuthenticationStrategy;
import com.aresstack.corenth.adyton.SecretUnavailableException;
import com.aresstack.corenth.astu.ResourceScheme;
import com.aresstack.corenth.astu.acropolis.chalcotheca.AcquisitionAccess;
import com.aresstack.corenth.astu.acropolis.chalcotheca.AcquisitionAccessPort;
import com.aresstack.corenth.astu.acropolis.chalcotheca.AcquisitionAccessRequest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Adyton-backed access station for the archive counter (#10 Slice 3).
 *
 * <p>Each authenticated scheme is registered with the Adyton {@link AccessRequest} and the
 * protocol {@link AuthenticationStrategy} that produce its handle. For a registered scheme the
 * station acquires a handle through the {@link AccessBroker} and returns it as an opaque
 * capability; Adyton resolves the secret material and the strategy consumes it, so neither the
 * counter nor this class sees passwords. Unregistered schemes, such as {@code file:}, need no
 * authentication and never contact the broker.
 *
 * <p>Adyton outcomes are mapped to typed results: {@link AuthCancelledException} to
 * {@code CANCELLED}, other {@link SecretUnavailableException}s to {@code UNAVAILABLE}, any
 * other {@link AccessException} to {@code FAILED}. Exception messages are not forwarded,
 * because a secret source might include sensitive detail in them.
 */
public final class BrokeredAcquisitionAccess implements AcquisitionAccessPort {

    private final AccessBroker broker;
    private final Map<ResourceScheme, AuthenticatedSource<?>> sources;

    private BrokeredAcquisitionAccess(AccessBroker broker, Map<ResourceScheme, AuthenticatedSource<?>> sources) {
        this.broker = broker;
        this.sources = Collections.unmodifiableMap(new LinkedHashMap<ResourceScheme, AuthenticatedSource<?>>(sources));
    }

    /** Starts a station on the given broker. */
    public static Builder on(AccessBroker broker) {
        return new Builder(broker);
    }

    @Override
    public AcquisitionAccess prepare(AcquisitionAccessRequest request) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        AuthenticatedSource<?> source = sources.get(request.target().scheme());
        if (source == null) {
            return AcquisitionAccess.notRequired();
        }
        try {
            AccessHandle handle = source.acquire(broker);
            if (handle == null) {
                return AcquisitionAccess.failed("Access broker returned no handle for " + source.targetSystem());
            }
            return AcquisitionAccess.granted(new HandleCapability(handle));
        } catch (AuthCancelledException e) {
            return AcquisitionAccess.cancelled("Credential request cancelled for " + source.targetSystem());
        } catch (SecretUnavailableException e) {
            return AcquisitionAccess.unavailable("No credential available for " + source.targetSystem());
        } catch (AccessException e) {
            return AcquisitionAccess.failed("Authentication failed for " + source.targetSystem());
        }
    }

    /** Returns whether a scheme requires authentication through this station. */
    public boolean authenticates(ResourceScheme scheme) {
        return sources.containsKey(scheme);
    }

    /** Builder that registers authenticated schemes explicitly. */
    public static final class Builder {

        private final AccessBroker broker;
        private final Map<ResourceScheme, AuthenticatedSource<?>> sources =
                new LinkedHashMap<ResourceScheme, AuthenticatedSource<?>>();

        private Builder(AccessBroker broker) {
            if (broker == null) throw new IllegalArgumentException("broker must not be null");
            this.broker = broker;
        }

        /** Registers one authenticated scheme; each scheme may be registered once. */
        public <H extends AccessHandle> Builder authenticate(ResourceScheme scheme,
                                                             AccessRequest request,
                                                             AuthenticationStrategy<H> strategy) {
            if (scheme == null) throw new IllegalArgumentException("scheme must not be null");
            if (request == null) throw new IllegalArgumentException("request must not be null");
            if (strategy == null) throw new IllegalArgumentException("strategy must not be null");
            if (sources.containsKey(scheme)) {
                throw new IllegalArgumentException("scheme already registered: " + scheme);
            }
            sources.put(scheme, new AuthenticatedSource<H>(request, strategy));
            return this;
        }

        public BrokeredAcquisitionAccess build() {
            return new BrokeredAcquisitionAccess(broker, sources);
        }
    }

    private static final class AuthenticatedSource<H extends AccessHandle> {
        private final AccessRequest request;
        private final AuthenticationStrategy<H> strategy;

        AuthenticatedSource(AccessRequest request, AuthenticationStrategy<H> strategy) {
            this.request = request;
            this.strategy = strategy;
        }

        H acquire(AccessBroker broker) throws AccessException, AuthCancelledException {
            return broker.acquire(request, strategy);
        }

        String targetSystem() {
            return request.targetSystem();
        }
    }
}
