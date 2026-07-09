package io.effects.samples.ecommerce.schedulable;

import io.effects.Either;
import io.effects.recipes.schedulable.SchedulableRequest;
import io.effects.recipes.schedulable.ScheduleLedger;
import java.time.Instant;

public class WarrantyPeriodicTask implements SchedulableRequest<String, Instant> {

    @Override
    public Either<String, Void> evaluateSchedule(ScheduleLedger<String, Instant> ledger, Instant triggerTime, Instant now) {
        if (triggerTime.isBefore(now)) {
            return Either.left("Task must be scheduled for a future time.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateReschedule(ScheduleLedger<String, Instant> ledger, Instant newTriggerTime, Instant now) {
        if (newTriggerTime.isBefore(now)) {
            return Either.left("Rescheduling trigger time must be in the future.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateExecution(ScheduleLedger<String, Instant> ledger, Instant now) {
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateCancellation(ScheduleLedger<String, Instant> ledger, Instant now) {
        return Either.right(null);
    }
}
