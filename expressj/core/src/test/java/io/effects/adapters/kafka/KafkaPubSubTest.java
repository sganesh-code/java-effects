package io.effects.adapters.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.effects.core.IO;
import io.effects.ports.Subscription;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class KafkaPubSubTest {

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

    // Map tracking active topic subscriptions for the test mock
    private static final Map<String, List<MockConsumer>> topicSubscribers = new ConcurrentHashMap<>();

    // Mock Kafka Producer
    private static class MockProducer implements Producer<String, String> {
        @Override
        public Future<RecordMetadata> send(ProducerRecord<String, String> record) {
            return send(record, null);
        }

        @Override
        public Future<RecordMetadata> send(ProducerRecord<String, String> record, Callback callback) {
            String topic = record.topic();
            String value = record.value();

            List<MockConsumer> consumers = topicSubscribers.get(topic);
            if (consumers != null) {
                for (MockConsumer consumer : consumers) {
                    consumer.addRecord(topic, value);
                }
            }

            TopicPartition tp = new TopicPartition(topic, 0);
            RecordMetadata metadata = new RecordMetadata(tp, 0L, 0, 0L, 0, 0);
            if (callback != null) {
                callback.onCompletion(metadata, null);
            }
            return CompletableFuture.completedFuture(metadata);
        }

        // Unused interface methods stubbed
        @Override public void flush() {}
        @Override public List<PartitionInfo> partitionsFor(String topic) { return Collections.emptyList(); }
        @Override public Map<MetricName, ? extends Metric> metrics() { return Collections.emptyMap(); }
        @Override public void close() {}
        @Override public void close(Duration timeout) {}
        @Override public void initTransactions() {}
        @Override public void beginTransaction() {}
        @Override public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets, String consumerGroupId) {}
        @Override public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata) {}
        @Override public void commitTransaction() {}
        @Override public void abortTransaction() {}
        @Override public org.apache.kafka.common.Uuid clientInstanceId(Duration timeout) { return org.apache.kafka.common.Uuid.randomUuid(); }
    }

    // Mock Kafka Consumer
    private static class MockConsumer implements Consumer<String, String> {
        private final LinkedBlockingQueue<ConsumerRecord<String, String>> recordQueue = new LinkedBlockingQueue<>();
        private final List<String> subscribedTopics = new CopyOnWriteArrayList<>();
        private final AtomicBoolean active = new AtomicBoolean(true);

        public void addRecord(String topic, String value) {
            recordQueue.add(new ConsumerRecord<>(topic, 0, 0, null, value));
        }

        @Override
        public void subscribe(Collection<String> topics) {
            subscribedTopics.addAll(topics);
            for (String topic : topics) {
                topicSubscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(this);
            }
        }

        @Override
        public ConsumerRecords<String, String> poll(Duration timeout) {
            List<ConsumerRecord<String, String>> polledList = new ArrayList<>();
            try {
                // Poll from queue with a brief wait
                ConsumerRecord<String, String> record = recordQueue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (record != null) {
                    polledList.add(record);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (polledList.isEmpty()) {
                return ConsumerRecords.empty();
            }

            Map<TopicPartition, List<ConsumerRecord<String, String>>> recordsMap = new HashMap<>();
            TopicPartition tp = new TopicPartition(polledList.get(0).topic(), 0);
            recordsMap.put(tp, polledList);
            return new ConsumerRecords<>(recordsMap);
        }

        @Deprecated
        @Override
        public ConsumerRecords<String, String> poll(long timeout) {
            return poll(Duration.ofMillis(timeout));
        }

        @Override
        public void commitSync() {
            // No-op for mock confirmation
        }

        @Override
        public void unsubscribe() {
            active.set(false);
            for (String topic : subscribedTopics) {
                List<MockConsumer> consumers = topicSubscribers.get(topic);
                if (consumers != null) {
                    consumers.remove(this);
                }
            }
            subscribedTopics.clear();
        }

        @Override
        public Set<String> subscription() {
            return new java.util.HashSet<>(subscribedTopics);
        }

        @Override
        public void close() {
            unsubscribe();
        }

        // Unused interface methods stubbed
        @Override public void subscribe(Collection<String> topics, ConsumerRebalanceListener listener) {}
        @Override public void assign(Collection<TopicPartition> partitions) {}
        @Override public void commitSync(Duration timeout) {}
        @Override public void commitSync(Map<TopicPartition, OffsetAndMetadata> offsets) {}
        @Override public void commitSync(Map<TopicPartition, OffsetAndMetadata> offsets, Duration timeout) {}
        @Override public void commitAsync() {}
        @Override public void commitAsync(OffsetCommitCallback callback) {}
        @Override public void commitAsync(Map<TopicPartition, OffsetAndMetadata> offsets, OffsetCommitCallback callback) {}
        @Override public void seek(TopicPartition partition, long offset) {}
        @Override public void seek(TopicPartition partition, OffsetAndMetadata offsetAndMetadata) {}
        @Override public void seekToBeginning(Collection<TopicPartition> partitions) {}
        @Override public void seekToEnd(Collection<TopicPartition> partitions) {}
        @Override public long position(TopicPartition partition) { return 0; }
        @Override public long position(TopicPartition partition, Duration timeout) { return 0; }
        @Override public OffsetAndMetadata committed(TopicPartition partition) { return null; }
        @Override public OffsetAndMetadata committed(TopicPartition partition, Duration timeout) { return null; }
        @Override public Map<TopicPartition, OffsetAndMetadata> committed(Set<TopicPartition> partitions) { return Collections.emptyMap(); }
        @Override public Map<TopicPartition, OffsetAndMetadata> committed(Set<TopicPartition> partitions, Duration timeout) { return Collections.emptyMap(); }
        @Override public Map<MetricName, ? extends Metric> metrics() { return Collections.emptyMap(); }
        @Override public List<PartitionInfo> partitionsFor(String topic) { return Collections.emptyList(); }
        @Override public List<PartitionInfo> partitionsFor(String topic, Duration timeout) { return Collections.emptyList(); }
        @Override public Map<String, List<PartitionInfo>> listTopics() { return Collections.emptyMap(); }
        @Override public Map<String, List<PartitionInfo>> listTopics(Duration timeout) { return Collections.emptyMap(); }
        @Override public Set<TopicPartition> paused() { return Collections.emptySet(); }
        @Override public void pause(Collection<TopicPartition> partitions) {}
        @Override public void resume(Collection<TopicPartition> partitions) {}
        @Override public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Map<TopicPartition, Long> timestampsToSearch) { return Collections.emptyMap(); }
        @Override public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Map<TopicPartition, Long> timestampsToSearch, Duration timeout) { return Collections.emptyMap(); }
        @Override public Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> partitions) { return Collections.emptyMap(); }
        @Override public Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> partitions, Duration timeout) { return Collections.emptyMap(); }
        @Override public Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> partitions) { return Collections.emptyMap(); }
        @Override public Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> partitions, Duration timeout) { return Collections.emptyMap(); }
        @Override public OptionalLong currentLag(TopicPartition topicPartition) { return OptionalLong.empty(); }
        @Override public ConsumerGroupMetadata groupMetadata() { return null; }
        @Override public void enforceRebalance() {}
        @Override public void enforceRebalance(String reason) {}
        @Override public void close(Duration timeout) { close(); }
        @Override public void wakeup() {}
        @Override public Set<TopicPartition> assignment() { return Collections.emptySet(); }
        @Override public void subscribe(java.util.regex.Pattern pattern, ConsumerRebalanceListener listener) {}
        @Override public void subscribe(java.util.regex.Pattern pattern) {}
        @Override public org.apache.kafka.common.Uuid clientInstanceId(Duration timeout) { return org.apache.kafka.common.Uuid.randomUuid(); }
    }

    @Test
    void testKafkaEventPublisherAndSubscriber() throws InterruptedException {
        MockProducer mockProducer = new MockProducer();
        MockConsumer mockConsumer = new MockConsumer();

        ObjectMapper mapper = new ObjectMapper();
        KafkaEventPublisher<TestEvent> publisher = new KafkaEventPublisher<>(mockProducer, mapper);
        KafkaEventSubscriber<TestEvent> subscriber = new KafkaEventSubscriber<>(mockConsumer, mapper);

        List<TestEvent> receivedEvents = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);

        // Subscribe to "TestEvent" topic (topic mapped from simple class name)
        Subscription subscription = subscriber.subscribe("TestEvent", event -> IO.delay(() -> {
            receivedEvents.add(event);
            latch.countDown();
            return null;
        })).unsafeRunSync();

        assertNotNull(subscription);

        // Wait slightly for virtual thread consumer poll loop to start
        Thread.sleep(200);

        // Publish event
        TestEvent originalEvent = new TestEvent("Hello from Kafka!");
        publisher.publish(originalEvent).unsafeRunSync();

        // Wait for event to arrive
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        // Verify correct propagation
        assertEquals(1, receivedEvents.size());
        assertEquals("Hello from Kafka!", receivedEvents.get(0).getMessage());

        // Unsubscribe
        subscription.unsubscribe().unsafeRunSync();
    }
}
