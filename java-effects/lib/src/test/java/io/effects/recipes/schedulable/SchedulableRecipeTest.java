package io.effects.recipes.schedulable;

import io.effects.Either;
import io.effects.IO;
import io.effects.ports.EventPublisher;
import io.effects.ports.StateRepository;
import io.effects.ports.TelemetryPort;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchedulableRecipeTest {

    // A concrete, behavioral domain request representing a timed reminder or task.
    // Exposes NO getter APIs for identity or initiators, satisfying our pure OOP guidelines.
    private static final class TimedTask implements SchedulableRequest {

        TimedTask() {}

        @Override
        public Either<String, Void> evaluateSchedule(ScheduleLedger ledger, Instant triggerTime, Instant now) {
            // Invariant: triggerTime must be in the future
            if (triggerTime.isBefore(now)) {
                return Either.left("Cannot schedule trigger time in the past: " + triggerTime);
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateReschedule(ScheduleLedger ledger, Instant newTriggerTime, Instant now) {
            // Invariant: new triggerTime must be in the future
            if (newTriggerTime.isBefore(now)) {
                return Either.left("Cannot reschedule trigger time in the past: " + newTriggerTime);
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateExecution(ScheduleLedger ledger, Instant now) {
            // Temporal Progress Invariant: triggerTime must have passed on the Clock
            if (now.isBefore(ledger.triggerTime())) {
                return Either.left("Cannot execute: trigger time (" + ledger.triggerTime() + ") has not yet arrived on clock (" + now + ")");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateCancellation(ScheduleLedger ledger, Instant now) {
            // Cancellation only allowed before execution
            if (ledger.status() != ScheduleLedger.Status.SCHEDULED) {
                return Either.left("Cannot cancel task in current status: " + ledger.status());
            }
            return Either.right(null);
        }
    }

    // 1. Initial Schedule & Double Scheduling Invariant
    @Test
    void testTimedTaskScheduling() {
        SchedulableProcess process = new SchedulableProcess();
        TimedTask task = new TimedTask();
        process.register("sched-1", task).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T18:00:00Z");
        Instant triggerTime = t0.plusSeconds(300); // 5 mins in future

        // Fails because trigger is in past
        Either<String, ScheduleLedger> pastResult = process.schedule("sched-1", "user-1", t0.minusSeconds(10), t0).unsafeRunSync();
        assertTrue(pastResult.isLeft());

        // Succeeds
        Either<String, ScheduleLedger> successResult = process.schedule("sched-1", "user-1", triggerTime, t0).unsafeRunSync();
        assertTrue(successResult.isRight());
        ScheduleLedger ledger = successResult.getRight();

        assertEquals(ScheduleLedger.Status.SCHEDULED, ledger.status());
        assertEquals(triggerTime, ledger.triggerTime());

        // Double schedule fails
        Either<String, ScheduleLedger> reSched = process.schedule("sched-1", "user-1", triggerTime.plusSeconds(10), t0).unsafeRunSync();
        assertTrue(reSched.isLeft());
        assertTrue(reSched.getLeft().contains("occurrence already scheduled"));
    }

    // 2. Adjusting Trigger Time & In-flight Rescheduling
    @Test
    void testTimedTaskRescheduling() {
        InMemoryStateRepository<String, ScheduleLedger> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<SchedulableEvent> publisher = new InMemoryEventPublisher<>();
        SchedulableProcess process = new SchedulableProcess(repository, publisher, new NoOpTelemetryPort());

        TimedTask task = new TimedTask();
        process.register("sched-2", task).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T18:30:00Z");
        Instant tTrigger = t0.plusSeconds(600); // 10 mins in future

        process.schedule("sched-2", "user-2", tTrigger, t0).unsafeRunSync();

        // Reschedule to past fails
        Either<String, ScheduleLedger> badResched = process.reschedule("sched-2", "user-2", t0.minusSeconds(10), "Reschedule past", t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(badResched.isLeft());

        // Reschedule to closer future succeeds
        Instant tNewTrigger = t0.plusSeconds(300); // 5 mins in future
        Either<String, ScheduleLedger> goodResched = process.reschedule("sched-2", "user-2", tNewTrigger, "Reschedule closer", t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(goodResched.isRight());
        ScheduleLedger ledger = goodResched.getRight();

        assertEquals(tNewTrigger, ledger.triggerTime());
        assertEquals(2, ledger.history().size()); // schedule, reschedule

        // Verify events
        List<SchedulableEvent> events = publisher.getPublishedEvents();
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof OccurrenceScheduled);
        assertTrue(events.get(1) instanceof OccurrenceRescheduled);
        assertEquals(tNewTrigger, ((OccurrenceRescheduled) events.get(1)).triggerTime());
    }

    // 3. Temporal Progress Trigger Invariant
    @Test
    void testTemporalProgressExecution() {
        InMemoryStateRepository<String, ScheduleLedger> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<SchedulableEvent> publisher = new InMemoryEventPublisher<>();
        SchedulableProcess process = new SchedulableProcess(repository, publisher, new NoOpTelemetryPort());

        TimedTask task = new TimedTask();
        process.register("sched-3", task).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T19:00:00Z");
        Instant tTrigger = t0.plusSeconds(300); // 5 mins in future

        process.schedule("sched-3", "user-3", tTrigger, t0).unsafeRunSync();

        // Fire before trigger time fails (Temporal Progress!)
        Either<String, ScheduleLedger> prematureFire = process.fire("sched-3", "SYSTEM", t0.plusSeconds(120)).unsafeRunSync();
        assertTrue(prematureFire.isLeft());
        assertTrue(prematureFire.getLeft().contains("Cannot execute: trigger time"));

        // Fire at trigger time succeeds
        Either<String, ScheduleLedger> successfulFire = process.fire("sched-3", "SYSTEM", tTrigger).unsafeRunSync();
        assertTrue(successfulFire.isRight());
        ScheduleLedger ledger = successfulFire.getRight();

        assertEquals(ScheduleLedger.Status.FIRED, ledger.status());
        assertTrue(ledger.isTerminal());

        // Attempt further reschedule on fired terminal task fails (Immutable Expiry)
        Either<String, ScheduleLedger> terminalResched = process.reschedule("sched-3", "user-3", tTrigger.plusSeconds(600), "Try adjust expired", tTrigger.plusSeconds(10)).unsafeRunSync();
        assertTrue(terminalResched.isLeft());
    }

    // 4. Occurrence Cancellation Flow
    @Test
    void testOccurrenceCancellation() {
        InMemoryStateRepository<String, ScheduleLedger> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<SchedulableEvent> publisher = new InMemoryEventPublisher<>();
        SchedulableProcess process = new SchedulableProcess(repository, publisher, new NoOpTelemetryPort());

        TimedTask task = new TimedTask();
        process.register("sched-4", task).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T19:30:00Z");
        Instant tTrigger = t0.plusSeconds(300);

        process.schedule("sched-4", "user-4", tTrigger, t0).unsafeRunSync();

        // Cancel succeeds
        Either<String, ScheduleLedger> cancelResult = process.cancel("sched-4", "user-4", "No longer required", t0.plusSeconds(30)).unsafeRunSync();
        assertTrue(cancelResult.isRight());
        ScheduleLedger ledger = cancelResult.getRight();

        assertEquals(ScheduleLedger.Status.CANCELLED, ledger.status());
        assertTrue(ledger.isTerminal());

        // Verify event
        List<SchedulableEvent> events = publisher.getPublishedEvents();
        assertEquals(2, events.size()); // scheduled, cancelled
        assertTrue(events.get(1) instanceof OccurrenceCancelled);
    }
}
