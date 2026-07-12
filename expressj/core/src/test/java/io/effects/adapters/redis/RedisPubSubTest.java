package io.effects.adapters.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.effects.core.IO;
import io.effects.ports.Subscription;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class RedisPubSubTest {

    // Simple test event class
    public static class TestEvent {
        private String message;

        public TestEvent() {}

        public TestEvent(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    // A lightweight Mock Jedis client that simulates in-memory Pub/Sub behavior
    private static class MockJedis extends Jedis {
        private final Map<String, List<JedisPubSub>> topicSubscribers;
        private final Map<JedisPubSub, Boolean> activeSubscriptions = new ConcurrentHashMap<>();

        public MockJedis(Map<String, List<JedisPubSub>> topicSubscribers) {
            super("localhost", 16379);
            this.topicSubscribers = topicSubscribers;
        }

        @Override
        public long publish(String channel, String message) {
            List<JedisPubSub> subs = topicSubscribers.get(channel);
            if (subs != null) {
                for (JedisPubSub sub : subs) {
                    sub.onMessage(channel, message);
                }
                return subs.size();
            }
            return 0L;
        }

        @Override
        public void subscribe(JedisPubSub jedisPubSub, String... channels) {
            activeSubscriptions.put(jedisPubSub, true);

            for (String channel : channels) {
                topicSubscribers.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(jedisPubSub);
            }

            // Blocking wait simulating the subscription stream loop
            try {
                while (activeSubscriptions.getOrDefault(jedisPubSub, false)) {
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                for (String channel : channels) {
                    List<JedisPubSub> subs = topicSubscribers.get(channel);
                    if (subs != null) {
                        subs.remove(jedisPubSub);
                    }
                }
            }
        }

        // Custom mock-specific unsubscribe helper to break the blocking loop in tests
        public void unsubscribeMock(JedisPubSub pubSub) {
            activeSubscriptions.put(pubSub, false);
        }
    }

    private static class MockJedisPool extends JedisPool {
        private final MockJedis mockJedis;

        public MockJedisPool(MockJedis mockJedis) {
            super("localhost", 16379);
            this.mockJedis = mockJedis;
        }

        @Override
        public Jedis getResource() {
            return mockJedis;
        }

        @Override
        public void close() {
            // No-op
        }
    }

    @Test
    void testRedisEventPublisherAndSubscriber() throws InterruptedException {
        Map<String, List<JedisPubSub>> topicSubscribers = new ConcurrentHashMap<>();
        MockJedis mockJedis = new MockJedis(topicSubscribers);
        MockJedisPool mockJedisPool = new MockJedisPool(mockJedis);

        ObjectMapper mapper = new ObjectMapper();
        RedisEventPublisher<TestEvent> publisher = new RedisEventPublisher<>(mockJedisPool, mapper);
        RedisEventSubscriber<TestEvent> subscriber = new RedisEventSubscriber<>(mockJedisPool, mapper);

        List<TestEvent> receivedEvents = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);

        // Capture reference to the generated JedisPubSub for custom test unsubscription
        List<JedisPubSub> capturedPubSub = Collections.synchronizedList(new ArrayList<>());
        
        // Subscribe to "TestEvent" topic
        Subscription subscription = subscriber.subscribe("TestEvent", event -> IO.delay(() -> {
            receivedEvents.add(event);
            latch.countDown();
            return null;
        })).unsafeRunSync();

        assertNotNull(subscription);

        // Wait slightly for the Virtual Thread subscriber loop to start and register
        Thread.sleep(200);

        // Find the registered pubsub instance in our mock
        JedisPubSub pubSub = null;
        for (List<JedisPubSub> subs : topicSubscribers.values()) {
            if (!subs.isEmpty()) {
                pubSub = subs.get(0);
                break;
            }
        }

        assertNotNull(pubSub, "Subscriber should have registered a JedisPubSub instance in mock");

        // Publish event
        TestEvent originalEvent = new TestEvent("Hello from Redis!");
        publisher.publish(originalEvent).unsafeRunSync();

        // Wait for event to arrive
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        // Verify content correctness
        assertEquals(1, receivedEvents.size());
        assertEquals("Hello from Redis!", receivedEvents.get(0).getMessage());

        // Unsubscribe
        subscription.unsubscribe().unsafeRunSync();
        mockJedis.unsubscribeMock(pubSub);
    }
}
