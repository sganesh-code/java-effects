package io.effects.adapters.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.effects.IO;
import io.effects.ports.EventPublisher;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * A robust, thread-safe Redis Pub/Sub implementation of the EventPublisher port.
 * Serializes generic events to polymorphic JSON envelopes and publishes them to Redis channels.
 *
 * @param <E> the type of events/messages this publisher supports
 */
public final class RedisEventPublisher<E> implements EventPublisher<E> {
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a RedisEventPublisher with a connection pool and JSON mapper.
     */
    public RedisEventPublisher(JedisPool jedisPool, ObjectMapper objectMapper) {
        this.jedisPool = java.util.Objects.requireNonNull(jedisPool);
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper);
    }

    /**
     * Constructs a RedisEventPublisher with a connection pool and default mapper.
     */
    public RedisEventPublisher(JedisPool jedisPool) {
        this(jedisPool, new ObjectMapper());
    }

    @Override
    public IO<Void> publish(E event) {
        return IO.delay(() -> {
            try {
                String eventClassName = event.getClass().getName();
                String eventPayload = objectMapper.writeValueAsString(event);
                
                RedisMessageEnvelope envelope = new RedisMessageEnvelope(eventClassName, eventPayload);
                String envelopeJson = objectMapper.writeValueAsString(envelope);
                
                // Determine channel topic name by event simple class name (e.g. "HoldCreated")
                String topic = event.getClass().getSimpleName();
                
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.publish(topic, envelopeJson);
                }
                return null;
            } catch (Exception e) {
                throw new RuntimeException("Failed to publish event to Redis Pub/Sub", e);
            }
        });
    }

    /**
     * Message envelope for polymorphic serialization/deserialization.
     */
    public record RedisMessageEnvelope(String className, String payload) {}
}
