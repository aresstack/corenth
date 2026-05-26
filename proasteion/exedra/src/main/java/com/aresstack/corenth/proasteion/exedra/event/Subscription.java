package com.aresstack.corenth.proasteion.exedra.event;

/**
 * Opaque handle returned by {@link UiEventBus#subscribe} that allows
 * the caller to unsubscribe without keeping a reference to the listener.
 */
public interface Subscription {

    /** Remove this subscription from the event bus. */
    void unsubscribe();
}
