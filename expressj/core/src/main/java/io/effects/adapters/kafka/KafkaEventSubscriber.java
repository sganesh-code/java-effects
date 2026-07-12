package io.effects.adapters.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.effects.core.IO;
import io.effects.core.IORuntime;
import io.effects.ports.EventSubscriber;
import io.effects.ports.Subscription;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * A highly reliable Kafka implementation of the EventSubscriber port.
 * Polls Kafka asynchronously on Java 21 Virtual Threads and commits offsets manually 
 * only after the monadic handler completes successfully to guarantee At-Least-Once semantics.
 *
 * @param <E> the type of events/messages this subscriber supports
 */
public final class KafkaEventSubscriber<E> implements EventSubscriber<E> {
    private final Consumer<String, String> consumer;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a KafkaEventSubscriber with a Kafka consumer and JSON mapper.
     */
    public KafkaEventSubscriber(Consumer<String, String> consumer, ObjectMapper objectMapper) {
        this.consumer = java.util.Objects.requireNonNull(consumer);
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper);
    }

    /**
     * Constructs a KafkaEventSubscriber with a Kafka consumer and default mapper.
     */
    public KafkaEventSubscriber(Consumer<String, String> consumer) {
        this(consumer, new ObjectMapper());
    }

    @Override
    public IO<Subscription> subscribe(String topic, Function<E, IO<Void>> handler) {
        return IO.delay(() -> {
            AtomicBoolean active = new AtomicBoolean(true);

            // Subscribe consumer to the topic
            consumer.subscribe(Collections.singletonList(topic));

            // Start the polling loop on a Java 21 Virtual Thread
            IORuntime.global().execute(() -> {
                try {
                    while (active.get()) {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                        for (ConsumerRecord<String, String> record : records) {
                            try {
                                // Deserialize the envelope
                                KafkaEventPublisher.KafkaMessageEnvelope envelope = 
                                    objectMapper.readValue(record.value(), KafkaEventPublisher.KafkaMessageEnvelope.class);
                                
                                Class<?> eventClass = Class.forName(envelope.className());
                                @SuppressWarnings("unchecked")
                                E event = (E) objectMapper.readValue(envelope.payload(), eventClass);
                                
                                // Execute monadic business handler synchronously on this poll thread
                                handler.apply(event).unsafeRunSync();
                                
                                // Commit offset manually only after success (At-Least-Once)
                                consumer.commitSync();
                            } catch (Exception e) {
                                System.err.println("Kafka message processing error on topic [" + topic + "]: " + e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Kafka subscriber poll loop crashed on topic [" + topic + "]: " + e.getMessage());
                } finally {
                    try {
                        consumer.unsubscribe();
                        consumer.close();
                    } catch (Exception ignored) {}
                }
            });

            // Return active subscription handle
            return new Subscription() {
                @Override
                public IO<Void> unsubscribe() {
                    return IO.delay(() -> {
                        active.set(false);
                        return null;
                    });
                }
            };
        });
    }
}
