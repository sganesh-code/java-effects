package io.effects.recipes.observable;

import io.effects.recipes.observable.models.*;

import io.effects.core.Either;

/**
 * A purely behavioral, non-anemic object representing an Event Subscriber or Observer.
 * Separates core business rules from monadic IO orchestration.
 */
public interface ObservableRequest<ID, E> {

    /**
     * Checks if this subscriber is authorized to subscribe to the given topic.
     */
    Either<String, Void> checkSubscriptionPermission(String topic);

    /**
     * Decides whether to deliver the event based on custom subscriber-specific filtering logic.
     */
    boolean shouldDeliver(E event);

    /**
     * Synchronous callback to notify the domain object of a successfully delivered event.
     */
    void onEventDelivered(E event);
}
