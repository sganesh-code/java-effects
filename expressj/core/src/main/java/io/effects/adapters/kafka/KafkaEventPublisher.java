package io.effects.adapters.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.effects.Either;
import io.effects.IO;
import io.effects.ports.EventPublisher;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * A highly performant, non-blocking Kafka implementation of the EventPublisher port.
 * Translates generic events into polymorphic JSON envelopes and sends them asynchronously to Kafka.
 *
 * @param <E> the type of events/messages this publisher supports
 */
public final class KafkaEventPublisher<E> implements EventPublisher<E> {
    private final Producer<String, String> producer;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a KafkaEventPublisher with a Kafka producer and JSON mapper.
     */
    public KafkaEventPublisher(Producer<String, String> producer, ObjectMapper objectMapper) {
        this.producer = java.util.Objects.requireNonNull(producer);
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper);
    }

    /**
     * Constructs a KafkaEventPublisher with a Kafka producer and default mapper.
     */
    public KafkaEventPublisher(Producer<String, String> producer) {
        this(producer, new ObjectMapper());
    }

    @Override
    public IO<Void> publish(E event) {
        return IO.async(cb -> {
            try {
                String eventClassName = event.getClass().getName();
                String eventPayload = objectMapper.writeValueAsString(event);
                
                KafkaMessageEnvelope envelope = new KafkaMessageEnvelope(eventClassName, eventPayload);
                String envelopeJson = objectMapper.writeValueAsString(envelope);
                
                // Route to a Kafka topic derived from the simple class name
                String topic = event.getClass().getSimpleName();
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, envelopeJson);
                
                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        cb.accept(Either.left(exception));
                    } else {
                        cb.accept(Either.right(null));
                    }
                });
            } catch (Exception e) {
                cb.accept(Either.left(e));
            }
        });
    }

    /**
     * Message envelope for Kafka polymorphic serialization/deserialization.
     */
    public record KafkaMessageEnvelope(String className, String payload) {}
}
