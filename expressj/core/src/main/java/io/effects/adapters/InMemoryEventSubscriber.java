package io.effects.adapters;

import io.effects.IO;
import io.effects.ports.EventSubscriber;
import io.effects.ports.Subscription;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A generalized, thread-safe, in-memory implementation of EventSubscriber for testing and choreography.
 *
 * @param <E> the type of events/messages processed by this subscriber
 */
public final class InMemoryEventSubscriber<E> implements EventSubscriber<E> {
    private final ConcurrentHashMap<String, List<Function<E, IO<Void>>>> subscriptions = new ConcurrentHashMap<>();

    @Override
    public IO<Subscription> subscribe(String topic, Function<E, IO<Void>> handler) {
        return IO.delay(() -> {
            subscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(handler);
            return new Subscription() {
                @Override
                public IO<Void> unsubscribe() {
                    return IO.delay(() -> {
                        List<Function<E, IO<Void>>> handlers = subscriptions.get(topic);
                        if (handlers != null) {
                            handlers.remove(handler);
                        }
                        return null;
                    });
                }
            };
        });
    }

    /**
     * Publishes an event to a specific topic, executing all registered handlers in sequence.
     * This is useful for simulating event flows in local memory and test environments.
     *
     * @param topic the topic name
     * @param event the event to publish
     * @return an IO computation that routes the event to all active subscribers of the topic
     */
    public IO<Void> publish(String topic, E event) {
        return IO.delay(() -> {
            List<Function<E, IO<Void>>> handlers = subscriptions.get(topic);
            if (handlers == null || handlers.isEmpty()) {
                return IO.of((Void) null);
            }
            IO<Void> computation = IO.of(null);
            for (Function<E, IO<Void>> handler : handlers) {
                computation = computation.flatMap(v -> handler.apply(event));
            }
            return computation;
        }).flatten();
    }

    /**
     * Clears all registered subscriptions.
     */
    public void clear() {
        subscriptions.clear();
    }
}
