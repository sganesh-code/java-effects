package io.effects.recipes.observable;

import java.time.Instant;

public record SubscriptionCancelled<ID, S>(
    ID ledgerId,
    S subscriberId,
    String topic,
    Instant occurredAt
) implements ObservableEvent<ID, S> {}
