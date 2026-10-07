package com.aresstack.corenth.astu.acropolis.chalcotheca;

/**
 * Runs the {@link ResourceArchiveRepository} contract against the in-memory reference
 * implementation.
 */
public class InMemoryResourceArchiveRepositoryTest extends ResourceArchiveRepositoryContractTest {

    @Override
    protected ResourceArchiveRepository createRepository() {
        return new InMemoryResourceArchiveRepository();
    }
}
