package com.aresstack.corenth.proasteion.application;

import com.aresstack.corenth.astu.acropolis.ResourceLifecycleCoordinator;
import com.aresstack.corenth.astu.acropolis.ResourceProcessingRunner;
import com.aresstack.corenth.astu.acropolis.SearchCoordinator;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceAccess;

import java.io.Closeable;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable application context produced by {@link CorenthComposition}.
 *
 * <p>It hands hosts (Exedra, a CLI, a server, tests) the composed use-case entry points as inner
 * contracts only. No Holkas, Deigma, Tamias or Adyton implementation type is reachable through
 * this API (ADR-0001, guard rail 2). The context owns the resources opened during composition
 * (lexical index, analyzer) and releases them in {@link #close()}.
 */
public final class CorenthApplication implements Closeable {

    private final ResourceLifecycleCoordinator resourceLifecycle;
    private final SearchCoordinator search;
    private final MediatedResourceAccess mediatedResourceAccess;
    private final List<Closeable> ownedResources;

    CorenthApplication(ResourceLifecycleCoordinator resourceLifecycle,
                       SearchCoordinator search,
                       MediatedResourceAccess mediatedResourceAccess,
                       List<Closeable> ownedResources) {
        if (resourceLifecycle == null) throw new IllegalArgumentException("resourceLifecycle must not be null");
        if (search == null) throw new IllegalArgumentException("search must not be null");
        if (mediatedResourceAccess == null) throw new IllegalArgumentException("mediatedResourceAccess must not be null");
        this.resourceLifecycle = resourceLifecycle;
        this.search = search;
        this.mediatedResourceAccess = mediatedResourceAccess;
        this.ownedResources = Collections.unmodifiableList(new ArrayList<Closeable>(ownedResources));
    }

    /** The resource lifecycle use case: mediated read, inspection, indexing and snapshot. */
    public ResourceLifecycleCoordinator resourceLifecycle() {
        return resourceLifecycle;
    }

    /** Lexical search over everything the lifecycle has indexed. */
    /** Returns the run entry point that processes a list of resources and records the run. */
    public ResourceProcessingRunner resourceProcessing() {
        return new ResourceProcessingRunner(resourceLifecycle, Clock.systemUTC());
    }

    public SearchCoordinator search() {
        return search;
    }

    /** The archive counter contract for hosts that browse or read resources under their own actor. */
    public MediatedResourceAccess mediatedResourceAccess() {
        return mediatedResourceAccess;
    }

    /** Releases the resources opened during composition, in reverse order of creation. */
    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (int i = ownedResources.size() - 1; i >= 0; i--) {
            try {
                ownedResources.get(i).close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
