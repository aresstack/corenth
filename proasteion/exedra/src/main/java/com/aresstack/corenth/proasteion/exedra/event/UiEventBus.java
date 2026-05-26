package com.aresstack.corenth.proasteion.exedra.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight typed UI event bus with unsubscribe handles.
 *
 * <p>Subscribers register for a specific event class and receive only events
 * assignable to that class. Each subscription returns a {@link Subscription}
 * handle that can be used to cleanly remove the listener later.
 *
 * <p>Listener failures are logged rather than silently swallowed.
 */
public final class UiEventBus {

    private static final Logger LOG = Logger.getLogger(UiEventBus.class.getName());

    private final Map<Class<?>, List<Consumer<?>>> typed = new ConcurrentHashMap<>();
    private final List<Consumer<UiEvent<?>>> anyListeners = new CopyOnWriteArrayList<>();

    /**
     * Subscribe to a specific event type.
     *
     * @param eventType the event class to listen for
     * @param listener  the listener
     * @param <T>       event type
     * @return a subscription handle for unsubscribing
     */
    public <T extends UiEvent<?>> Subscription subscribe(Class<T> eventType, Consumer<T> listener) {
        if (eventType == null || listener == null) {
            throw new IllegalArgumentException("eventType and listener must not be null");
        }
        List<Consumer<?>> list = typed.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        list.add(listener);
        return () -> list.remove(listener);
    }

    /**
     * Subscribe to all events regardless of type.
     *
     * @param listener wildcard listener
     * @return a subscription handle
     */
    public Subscription subscribe(Consumer<UiEvent<?>> listener) {
        if (listener == null) throw new IllegalArgumentException("listener must not be null");
        anyListeners.add(listener);
        return () -> anyListeners.remove(listener);
    }

    /**
     * Publish an event to all matching subscribers.
     * Listener exceptions are logged at WARNING level.
     */
    @SuppressWarnings("unchecked")
    public void publish(UiEvent<?> event) {
        if (event == null) return;

        // Fan-out to typed listeners
        for (Map.Entry<Class<?>, List<Consumer<?>>> entry : typed.entrySet()) {
            if (entry.getKey().isInstance(event)) {
                for (Consumer<?> raw : entry.getValue()) {
                    try {
                        ((Consumer<UiEvent<?>>) raw).accept(event);
                    } catch (Throwable t) {
                        LOG.log(Level.WARNING, "Listener failed for event " + event.getClass().getSimpleName(), t);
                    }
                }
            }
        }

        // Fan-out to wildcard listeners
        for (Consumer<UiEvent<?>> l : anyListeners) {
            try {
                l.accept(event);
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "Wildcard listener failed for event " + event.getClass().getSimpleName(), t);
            }
        }
    }
}
