package io.effects.recipes.observable;

import java.time.Instant;

public record SubscriptionRecord<S>(
    String topic,
    S subscriberId,
    SubscriptionStatus status,
    Instant subscribedAt
) {}
