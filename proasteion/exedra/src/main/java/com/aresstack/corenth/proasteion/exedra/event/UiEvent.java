package com.aresstack.corenth.proasteion.exedra.event;

/**
 * Marker interface for all UI events published on the {@link UiEventBus}.
 *
 * @param <T> payload type
 */
public interface UiEvent<T> {

    /** The event payload (may be null for signal-only events). */
    T getPayload();
}
