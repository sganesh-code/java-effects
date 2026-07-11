package io.effects.recipes.observable;

import java.time.Instant;

public sealed interface ObservableEvent<ID, S> permits SubscriptionCreated, SubscriptionCancelled {
    ID ledgerId();
    S subscriberId();
    String topic();
    Instant occurredAt();
}
