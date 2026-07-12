package io.effects.ports;

import io.effects.core.IO;
import io.effects.adapters.InMemoryEventSubscriber;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EventPubSubTest {

    @Test
    void testBasicPublishSubscribe() {
        InMemoryEventSubscriber<String> subscriber = new InMemoryEventSubscriber<>();
        List<String> receivedEvents = Collections.synchronizedList(new ArrayList<>());

        // Subscribe to a topic
        Subscription sub = subscriber.subscribe("test-topic", event -> IO.delay(() -> {
            receivedEvents.add(event);
            return null;
        })).unsafeRunSync();

        assertNotNull(sub);

        // Publish an event to the topic
        subscriber.publish("test-topic", "Hello, World!").unsafeRunSync();

        // Verify message was received
        assertEquals(1, receivedEvents.size());
        assertEquals("Hello, World!", receivedEvents.get(0));
    }

    @Test
    void testMultipleSubscribers() {
        InMemoryEventSubscriber<String> subscriber = new InMemoryEventSubscriber<>();
        List<String> sub1Events = Collections.synchronizedList(new ArrayList<>());
        List<String> sub2Events = Collections.synchronizedList(new ArrayList<>());

        subscriber.subscribe("news-topic", event -> IO.delay(() -> {
            sub1Events.add(event);
            return null;
        })).unsafeRunSync();

        subscriber.subscribe("news-topic", event -> IO.delay(() -> {
            sub2Events.add(event);
            return null;
        })).unsafeRunSync();

        // Publish
        subscriber.publish("news-topic", "Breaking News").unsafeRunSync();

        // Both should receive the event
        assertEquals(1, sub1Events.size());
        assertEquals("Breaking News", sub1Events.get(0));

        assertEquals(1, sub2Events.size());
        assertEquals("Breaking News", sub2Events.get(0));
    }

    @Test
    void testUnsubscribe() {
        InMemoryEventSubscriber<String> subscriber = new InMemoryEventSubscriber<>();
        List<String> receivedEvents = Collections.synchronizedList(new ArrayList<>());

        Subscription sub = subscriber.subscribe("updates", event -> IO.delay(() -> {
            receivedEvents.add(event);
            return null;
        })).unsafeRunSync();

        // Publish first update
        subscriber.publish("updates", "Update 1").unsafeRunSync();
        assertEquals(1, receivedEvents.size());
        assertEquals("Update 1", receivedEvents.get(0));

        // Unsubscribe
        sub.unsubscribe().unsafeRunSync();

        // Publish second update
        subscriber.publish("updates", "Update 2").unsafeRunSync();

        // Should not have received the second update
        assertEquals(1, receivedEvents.size());
    }

    @Test
    void testConcurrencyPublishSubscribe() throws InterruptedException {
        InMemoryEventSubscriber<String> subscriber = new InMemoryEventSubscriber<>();
        AtomicInteger eventCounter = new AtomicInteger(0);

        int totalPublishers = 10;
        int eventsPerPublisher = 100;
        CountDownLatch latch = new CountDownLatch(totalPublishers);
        ExecutorService executor = Executors.newFixedThreadPool(totalPublishers);

        // Subscribe to a topic
        subscriber.subscribe("concurrent-topic", event -> IO.delay(() -> {
            eventCounter.incrementAndGet();
            return null;
        })).unsafeRunSync();

        // Publish concurrently from multiple threads
        for (int i = 0; i < totalPublishers; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < eventsPerPublisher; j++) {
                        subscriber.publish("concurrent-topic", "event").unsafeRunSync();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);

        // All events should be delivered safely without race conditions
        assertEquals(totalPublishers * eventsPerPublisher, eventCounter.get());
    }
}
