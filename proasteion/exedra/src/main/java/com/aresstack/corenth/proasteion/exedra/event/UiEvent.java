package com.aresstack.corenth.proasteion.exedra.event;

/**
 * Base interface for all UI events published on the {@link UiEventBus}.
 * Events carry a timestamp and an optional typed payload.
 *
 * @param <T> payload type
 */
public interface UiEvent<T> {

    /** The event payload (may be null for signal-only events). */
    T getPayload();

    /** Timestamp in millis when the event was created. */
    long getTimestamp();
}
