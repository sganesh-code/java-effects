package io.effects.recipes.observable;

import io.effects.Either;
import io.effects.recipes.TransitionResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A rich, non-anemic domain aggregate root representing the state ledger of active subscriptions.
 */
public final class ObservableLedger<ID, S> {
    private final ID ledgerId;
    private final List<SubscriptionRecord<S>> subscriptions = new ArrayList<>();

    public ObservableLedger(ID ledgerId) {
        this.ledgerId = Objects.requireNonNull(ledgerId);
    }

    public ID ledgerId() { return ledgerId; }

    public synchronized List<SubscriptionRecord<S>> subscriptions() {
        return Collections.unmodifiableList(new ArrayList<>(subscriptions));
    }

    public synchronized boolean isSubscribed(String topic, S subscriberId) {
        return subscriptions.stream().anyMatch(sub -> 
            sub.topic().equals(topic) && 
            sub.subscriberId().equals(subscriberId) && 
            sub.status() == SubscriptionStatus.ACTIVE
        );
    }

    /**
     * Behavioral Transition: Registers a subscription and yields the updated state and created event.
     */
    public synchronized Either<String, TransitionResult<ObservableLedger<ID, S>, ObservableEvent<ID, S>>> subscribe(
        String topic,
        S subscriberId,
        Instant now
    ) {
        if (isSubscribed(topic, subscriberId)) {
            return Either.left("Subscriber already subscribed to topic [" + topic + "]");
        }

        SubscriptionRecord<S> record = new SubscriptionRecord<>(topic, subscriberId, SubscriptionStatus.ACTIVE, now);
        subscriptions.add(record);

        return Either.right(new TransitionResult<>(this, new SubscriptionCreated<>(ledgerId, subscriberId, topic, now)));
    }

    /**
     * Behavioral Transition: Cancels a subscription and yields the updated state and cancelled event.
     */
    public synchronized Either<String, TransitionResult<ObservableLedger<ID, S>, ObservableEvent<ID, S>>> unsubscribe(
        String topic,
        S subscriberId,
        Instant now
    ) {
        if (!isSubscribed(topic, subscriberId)) {
            return Either.left("No active subscription found for topic [" + topic + "]");
        }

        // Mark old active subscription as cancelled
        subscriptions.removeIf(sub -> sub.topic().equals(topic) && sub.subscriberId().equals(subscriberId));
        subscriptions.add(new SubscriptionRecord<>(topic, subscriberId, SubscriptionStatus.CANCELLED, now));

        return Either.right(new TransitionResult<>(this, new SubscriptionCancelled<>(ledgerId, subscriberId, topic, now)));
    }
}
