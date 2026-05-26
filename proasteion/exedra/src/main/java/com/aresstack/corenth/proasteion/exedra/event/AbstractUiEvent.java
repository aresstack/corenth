package com.aresstack.corenth.proasteion.exedra.event;

/**
 * Convenience base class for events with automatic timestamping.
 *
 * @param <T> payload type
 */
public abstract class AbstractUiEvent<T> implements UiEvent<T> {

    private final T payload;
    private final long timestamp;

    protected AbstractUiEvent(T payload) {
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public T getPayload() {
        return payload;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }
}
