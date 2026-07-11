package io.effects.ports;

import io.effects.IO;
import java.util.function.Function;

/**
 * A generalized, type-safe port representing external subscription and consumption capabilities.
 *
 * @param <E> the type of events/messages this subscriber processes
 */
public interface EventSubscriber<E> {

    /**
     * Subscribes a message handler to a specific topic or channel.
     *
     * @param topic the topic or channel name to subscribe to
     * @param handler a monadic function that processes the event and returns an IO computation
     * @return an IO computation returning a Subscription that can be used to cancel the subscription
     */
    IO<Subscription> subscribe(String topic, Function<E, IO<Void>> handler);
}
