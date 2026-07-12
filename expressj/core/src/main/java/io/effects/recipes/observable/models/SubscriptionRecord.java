package io.effects.recipes.observable.models;

import io.effects.recipes.observable.*;
import io.effects.recipes.observable.models.*;

import java.time.Instant;

public record SubscriptionRecord<S>(
    String topic,
    S subscriberId,
    SubscriptionStatus status,
    Instant subscribedAt
) {}
