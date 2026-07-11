package io.effects.adapters.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.effects.IO;
import io.effects.IORuntime;
import io.effects.ports.EventSubscriber;
import io.effects.ports.Subscription;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import java.util.function.Function;

/**
 * A robust, non-blocking Redis Pub/Sub implementation of the EventSubscriber port.
 * Runs Jedis subscription loops on Java 21 Virtual Threads and routes deserialized events to handlers.
 *
 * @param <E> the type of events/messages this subscriber supports
 */
public final class RedisEventSubscriber<E> implements EventSubscriber<E> {
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a RedisEventSubscriber with a connection pool and JSON mapper.
     */
    public RedisEventSubscriber(JedisPool jedisPool, ObjectMapper objectMapper) {
        this.jedisPool = java.util.Objects.requireNonNull(jedisPool);
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper);
    }

    /**
     * Constructs a RedisEventSubscriber with a connection pool and default mapper.
     */
    public RedisEventSubscriber(JedisPool jedisPool) {
        this(jedisPool, new ObjectMapper());
    }

    @Override
    public IO<Subscription> subscribe(String topic, Function<E, IO<Void>> handler) {
        return IO.delay(() -> {
            JedisPubSub pubSub = new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                    try {
                        // Deserialize the message envelope
                        RedisEventPublisher.RedisMessageEnvelope envelope = 
                            objectMapper.readValue(message, RedisEventPublisher.RedisMessageEnvelope.class);
                        
                        // Dynamically load class and deserialize payload
                        Class<?> eventClass = Class.forName(envelope.className());
                        @SuppressWarnings("unchecked")
                        E event = (E) objectMapper.readValue(envelope.payload(), eventClass);
                        
                        // Run the monadic handler synchronously on the virtual thread
                        handler.apply(event).unsafeRunSync();
                    } catch (Exception e) {
                        System.err.println("Error processing Redis message on topic [" + topic + "]: " + e.getMessage());
                    }
                }
            };

            // Run the blocking Jedis subscribe loop on a Java 21 Virtual Thread
            IORuntime.global().execute(() -> {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.subscribe(pubSub, topic);
                } catch (Exception e) {
                    System.err.println("Redis subscription error on topic [" + topic + "]: " + e.getMessage());
                }
            });

            // Return active subscription handle
            return () -> IO.delay(() -> {
                try {
                    pubSub.unsubscribe();
                } catch (Exception ignored) {
                    // Catch exceptions when not bound to a real Redis connection socket in mocks
                }
                return null;
            });
        });
    }
}
