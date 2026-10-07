package com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes;

/**
 * Tell an embedding adapter whether a text is a search query or an indexed passage.
 *
 * <p>Asymmetric retrieval models (for example the E5 family) embed queries and passages
 * differently. The adapter decides how to honour the purpose, such as by adding a model
 * specific prefix; symmetric models may ignore it.
 */
public enum EmbeddingPurpose {
    QUERY,
    PASSAGE
}
