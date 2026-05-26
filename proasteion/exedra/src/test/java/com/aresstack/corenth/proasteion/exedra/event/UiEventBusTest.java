package com.aresstack.corenth.proasteion.exedra.event;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class UiEventBusTest {

    private UiEventBus bus;

    @Before
    public void setUp() {
        bus = new UiEventBus();
    }

    @Test
    public void typedSubscription_receivesMatchingEvents() {
        List<String> received = new ArrayList<>();
        bus.subscribe(TestEvent.class, e -> received.add(e.getPayload()));

        bus.publish(new TestEvent("hello"));
        assertEquals(1, received.size());
        assertEquals("hello", received.get(0));
    }

    @Test
    public void typedSubscription_ignoresNonMatchingEvents() {
        List<String> received = new ArrayList<>();
        bus.subscribe(TestEvent.class, e -> received.add(e.getPayload()));

        bus.publish(new OtherEvent(42));
        assertTrue(received.isEmpty());
    }

    @Test
    public void wildcardSubscription_receivesAllEvents() {
        List<UiEvent<?>> received = new ArrayList<>();
        bus.subscribe(received::add);

        bus.publish(new TestEvent("a"));
        bus.publish(new OtherEvent(1));
        assertEquals(2, received.size());
    }

    @Test
    public void unsubscribe_preventsDelivery() {
        List<String> received = new ArrayList<>();
        Subscription sub = bus.subscribe(TestEvent.class, e -> received.add(e.getPayload()));

        bus.publish(new TestEvent("first"));
        sub.unsubscribe();
        bus.publish(new TestEvent("second"));

        assertEquals(1, received.size());
        assertEquals("first", received.get(0));
    }

    @Test
    public void publish_null_isIgnored() {
        List<String> received = new ArrayList<>();
        bus.subscribe(TestEvent.class, e -> received.add(e.getPayload()));
        bus.publish(null);
        assertTrue(received.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void subscribe_nullType_throws() {
        bus.subscribe(null, e -> { });
    }

    @Test(expected = IllegalArgumentException.class)
    public void subscribe_nullListener_throws() {
        bus.subscribe(TestEvent.class, null);
    }

    @Test
    public void event_hasTimestamp() {
        long before = System.currentTimeMillis();
        TestEvent event = new TestEvent("ts");
        long after = System.currentTimeMillis();
        assertTrue(event.getTimestamp() >= before);
        assertTrue(event.getTimestamp() <= after);
    }

    @Test
    public void listenerFailure_doesNotPreventOtherListeners() {
        List<String> received = new ArrayList<>();
        bus.subscribe(TestEvent.class, e -> { throw new RuntimeException("fail"); });
        bus.subscribe(TestEvent.class, e -> received.add(e.getPayload()));

        bus.publish(new TestEvent("ok"));
        assertEquals(1, received.size());
        assertEquals("ok", received.get(0));
    }

    // ---- test event types ----

    private static class TestEvent extends AbstractUiEvent<String> {
        TestEvent(String payload) { super(payload); }
    }

    private static class OtherEvent extends AbstractUiEvent<Integer> {
        OtherEvent(int payload) { super(payload); }
    }
}
