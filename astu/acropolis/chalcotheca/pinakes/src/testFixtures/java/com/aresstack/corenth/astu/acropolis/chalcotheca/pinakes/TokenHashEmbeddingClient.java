package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Provide a deterministic, dependency-free {@link EmbeddingClient} for contract tests.
 *
 * <p>The client hashes lower-cased word tokens into signed buckets (feature hashing). Texts
 * that share words get similar vectors, which is enough to exercise semantic indexing,
 * fusion and the retrieval contract. It is <strong>not</strong> a semantic model: synonyms
 * stay unrelated unless a test registers them. A test that passes with this client proves
 * contract coverage, never the behaviour of a real embedding runtime.
 *
 * <p>Purposes are recorded but otherwise ignored; the client is symmetric.
 */
public final class TokenHashEmbeddingClient implements EmbeddingClient {

    private final EmbeddingModel model;
    private final List<String[]> synonymGroups;
    private final List<String> calls = new ArrayList<String>();
    private EmbeddingException nextFailure;

    public TokenHashEmbeddingClient(int dimension) {
        this(new EmbeddingModel("test/token-hash-" + dimension, dimension), Collections.<String[]>emptyList());
    }

    private TokenHashEmbeddingClient(EmbeddingModel model, List<String[]> synonymGroups) {
        this.model = model;
        this.synonymGroups = synonymGroups;
    }

    /** Return a client that maps every word of the group onto the group's first word. */
    public TokenHashEmbeddingClient withSynonyms(String... group) {
        List<String[]> groups = new ArrayList<String[]>(synonymGroups);
        groups.add(group.clone());
        return new TokenHashEmbeddingClient(model, groups);
    }

    /** Make the next {@link #embed} call fail with the given exception. */
    public void failNextCall(EmbeddingException failure) {
        this.nextFailure = failure;
    }

    /** Return a log of every embedded text, prefixed with its purpose. */
    public List<String> calls() {
        return Collections.unmodifiableList(new ArrayList<String>(calls));
    }

    @Override
    public EmbeddingModel model() {
        return model;
    }

    @Override
    public List<EmbeddingVector> embed(EmbeddingPurpose purpose, List<String> texts) throws EmbeddingException {
        if (nextFailure != null) {
            EmbeddingException failure = nextFailure;
            nextFailure = null;
            throw failure;
        }
        List<EmbeddingVector> vectors = new ArrayList<EmbeddingVector>(texts.size());
        for (String text : texts) {
            calls.add(purpose + ":" + text);
            vectors.add(new EmbeddingVector(hash(text)));
        }
        return vectors;
    }

    private float[] hash(String text) {
        float[] values = new float[model.dimension()];
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.isEmpty()) {
                continue;
            }
            int hash = canonical(token).hashCode();
            int bucket = (hash & 0x7fffffff) % values.length;
            values[bucket] += (hash & 0x80000000) == 0 ? 1f : -1f;
        }
        return values;
    }

    private String canonical(String token) {
        for (String[] group : synonymGroups) {
            for (String word : group) {
                if (word.equalsIgnoreCase(token)) {
                    return group[0].toLowerCase(Locale.ROOT);
                }
            }
        }
        return token;
    }
}
