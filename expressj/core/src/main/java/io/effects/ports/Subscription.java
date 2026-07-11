package io.effects.ports;

import io.effects.IO;

/**
 * Represents an active subscription or message listener registration.
 * Allows the consumer to cancel and clean up the subscription.
 */
public interface Subscription {

    /**
     * Cancels the active subscription and frees any allocated resources.
     *
     * @return an IO computation that cancels the subscription
     */
    IO<Void> unsubscribe();
}
