package io.effects.recipes.observable.models;

import io.effects.recipes.observable.*;
import io.effects.recipes.observable.models.*;

import java.time.Instant;

public record SubscriptionCreated<ID, S>(
    ID ledgerId,
    S subscriberId,
    String topic,
    Instant occurredAt
) implements ObservableEvent<ID, S> {}
