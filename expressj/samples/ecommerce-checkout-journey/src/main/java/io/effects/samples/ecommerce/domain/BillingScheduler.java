package io.effects.samples.ecommerce.domain;

import io.effects.core.Either;
import io.effects.recipes.schedulable.*;
import io.effects.recipes.schedulable.models.*;
import java.time.Instant;

public class BillingScheduler implements SchedulableRequest<String, Instant> {
    private final String orderId;
    private final SchedulableProcess<String, Instant> schedulerProcess;

    public BillingScheduler(String orderId) {
        this.orderId = orderId;
        this.schedulerProcess = new SchedulableProcess<>();
    }

    public void scheduleTask(String actorId, Instant runTime, Instant scheduleTime) {
        DomainLogger.info("[SCHEDULE] Scheduling billing settlement check to run at: " + runTime);
        schedulerProcess.register(orderId, this).unsafeRunSync();
        var res = schedulerProcess.schedule(orderId, actorId, runTime, scheduleTime).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Scheduling failed: " + res.getLeft());
        }
    }

    public void fire(String actorId, Instant runTime) {
        DomainLogger.info("[SCHEDULE] Cron scheduled. Simulating scheduler firing at: " + runTime);
        var res = schedulerProcess.fire(orderId, actorId, runTime).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Firing failed: " + res.getLeft());
        }
        DomainLogger.info("[SCHEDULE] Fired. Running rating cycle on meter ledger for support overages...");
    }

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
