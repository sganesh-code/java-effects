package io.effects.recipes.schedulable;

import io.effects.Either;
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

    // A custom, clean, non-anemic domain representation of a Trigger specification.
    private record TriggerSpec(Instant time, String label) {
        public TriggerSpec {
            java.util.Objects.requireNonNull(time);
            java.util.Objects.requireNonNull(label);
        }

        public boolean isBefore(Instant other) {
            return this.time.isBefore(other);
        }

        public boolean equalsTrigger(TriggerSpec other) {
            return this.time.equals(other.time()) && this.label.equals(other.label());
        }
    }

    // A concrete, behavioral domain request representing a timed reminder or task.
    // Exposes NO getter APIs for identity or initiators, satisfying our pure OOP guidelines.
    private static final class TimedTask implements SchedulableRequest<String, TriggerSpec> {

        TimedTask() {}

        @Override
        public Either<String, Void> evaluateSchedule(ScheduleLedger<String, TriggerSpec> ledger, TriggerSpec triggerTime, Instant now) {
            // Invariant: triggerTime must be in the future
            if (triggerTime.isBefore(now)) {
                return Either.left("Cannot schedule trigger time in the past: " + triggerTime.time());
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateReschedule(ScheduleLedger<String, TriggerSpec> ledger, TriggerSpec newTriggerTime, Instant now) {
            // Invariant: new triggerTime must be in the future
            if (newTriggerTime.isBefore(now)) {
                return Either.left("Cannot reschedule trigger time in the past: " + newTriggerTime.time());
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateExecution(ScheduleLedger<String, TriggerSpec> ledger, Instant now) {
            // Temporal Progress Invariant: triggerTime must have passed on the Clock
            if (now.isBefore(ledger.triggerTime().time())) {
                return Either.left("Cannot execute: trigger time (" + ledger.triggerTime().time() + ") has not yet arrived on clock (" + now + ")");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateCancellation(ScheduleLedger<String, TriggerSpec> ledger, Instant now) {
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
        SchedulableProcess<String, TriggerSpec> process = new SchedulableProcess<>();
        TimedTask task = new TimedTask();
        process.register("sched-1", task).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T18:00:00Z");
        TriggerSpec triggerTime = new TriggerSpec(t0.plusSeconds(300), "reminder-1"); // 5 mins in future

        // Fails because trigger is in past
        Either<String, ScheduleLedger<String, TriggerSpec>> pastResult = process.schedule("sched-1", "user-1", new TriggerSpec(t0.minusSeconds(10), "reminder-past"), t0).unsafeRunSync();
        assertTrue(pastResult.isLeft());

        // Succeeds
        Either<String, ScheduleLedger<String, TriggerSpec>> successResult = process.schedule("sched-1", "user-1", triggerTime, t0).unsafeRunSync();
        assertTrue(successResult.isRight());
        ScheduleLedger<String, TriggerSpec> ledger = successResult.getRight();

        assertEquals(ScheduleLedger.Status.SCHEDULED, ledger.status());
        assertEquals(triggerTime, ledger.triggerTime());

        // Double schedule fails
        Either<String, ScheduleLedger<String, TriggerSpec>> reSched = process.schedule("sched-1", "user-1", new TriggerSpec(triggerTime.time().plusSeconds(10), "reminder-double"), t0).unsafeRunSync();
        assertTrue(reSched.isLeft());
        assertTrue(reSched.getLeft().contains("occurrence already scheduled"));
    }

    // 2. Adjusting Trigger Time & In-flight Rescheduling
    @Test
    void testTimedTaskRescheduling() {
        InMemoryStateRepository<String, ScheduleLedger<String, TriggerSpec>> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<SchedulableEvent<String, TriggerSpec>> publisher = new InMemoryEventPublisher<>();
        SchedulableProcess<String, TriggerSpec> process = new SchedulableProcess<>(repository, publisher, new NoOpTelemetryPort());

        TimedTask task = new TimedTask();
        process.register("sched-2", task).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T18:30:00Z");
        TriggerSpec tTrigger = new TriggerSpec(t0.plusSeconds(600), "task-1"); // 10 mins in future

        process.schedule("sched-2", "user-2", tTrigger, t0).unsafeRunSync();

        // Reschedule to past fails
        Either<String, ScheduleLedger<String, TriggerSpec>> badResched = process.reschedule("sched-2", "user-2", new TriggerSpec(t0.minusSeconds(10), "resched-past"), "Reschedule past", t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(badResched.isLeft());

        // Reschedule to closer future succeeds
        TriggerSpec tNewTrigger = new TriggerSpec(t0.plusSeconds(300), "task-rescheduled"); // 5 mins in future
        Either<String, ScheduleLedger<String, TriggerSpec>> goodResched = process.reschedule("sched-2", "user-2", tNewTrigger, "Reschedule closer", t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(goodResched.isRight());
        ScheduleLedger<String, TriggerSpec> ledger = goodResched.getRight();

        assertEquals(tNewTrigger, ledger.triggerTime());
        assertEquals(2, ledger.history().size()); // schedule, reschedule

        // Verify events
        List<SchedulableEvent<String, TriggerSpec>> events = publisher.getPublishedEvents();
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof OccurrenceScheduled);
        assertTrue(events.get(1) instanceof OccurrenceRescheduled);
        assertEquals(tNewTrigger, ((OccurrenceRescheduled<String, TriggerSpec>) events.get(1)).triggerTime());
    }

    // 3. Temporal Progress Trigger Invariant
    @Test
    void testTemporalProgressExecution() {
        InMemoryStateRepository<String, ScheduleLedger<String, TriggerSpec>> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<SchedulableEvent<String, TriggerSpec>> publisher = new InMemoryEventPublisher<>();
        SchedulableProcess<String, TriggerSpec> process = new SchedulableProcess<>(repository, publisher, new NoOpTelemetryPort());

        TimedTask task = new TimedTask();
        process.register("sched-3", task).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T19:00:00Z");
        TriggerSpec tTrigger = new TriggerSpec(t0.plusSeconds(300), "fire-task"); // 5 mins in future

        process.schedule("sched-3", "user-3", tTrigger, t0).unsafeRunSync();

        // Fire before trigger time fails (Temporal Progress!)
        Either<String, ScheduleLedger<String, TriggerSpec>> prematureFire = process.fire("sched-3", "SYSTEM", t0.plusSeconds(120)).unsafeRunSync();
        assertTrue(prematureFire.isLeft());
        assertTrue(prematureFire.getLeft().contains("Cannot execute: trigger time"));

        // Fire at trigger time succeeds
        Either<String, ScheduleLedger<String, TriggerSpec>> successfulFire = process.fire("sched-3", "SYSTEM", tTrigger.time()).unsafeRunSync();
        assertTrue(successfulFire.isRight());
        ScheduleLedger<String, TriggerSpec> ledger = successfulFire.getRight();

        assertEquals(ScheduleLedger.Status.FIRED, ledger.status());
        assertTrue(ledger.isTerminal());

        // Attempt further reschedule on fired terminal task fails (Immutable Expiry)
        Either<String, ScheduleLedger<String, TriggerSpec>> terminalResched = process.reschedule("sched-3", "user-3", new TriggerSpec(tTrigger.time().plusSeconds(600), "terminal-adjust"), "Try adjust expired", tTrigger.time().plusSeconds(10)).unsafeRunSync();
        assertTrue(terminalResched.isLeft());
    }

    // 4. Occurrence Cancellation Flow
    @Test
    void testOccurrenceCancellation() {
        InMemoryStateRepository<String, ScheduleLedger<String, TriggerSpec>> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<SchedulableEvent<String, TriggerSpec>> publisher = new InMemoryEventPublisher<>();
        SchedulableProcess<String, TriggerSpec> process = new SchedulableProcess<>(repository, publisher, new NoOpTelemetryPort());

        TimedTask task = new TimedTask();
        process.register("sched-4", task).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T19:30:00Z");
        TriggerSpec tTrigger = new TriggerSpec(t0.plusSeconds(300), "cancel-task");

        process.schedule("sched-4", "user-4", tTrigger, t0).unsafeRunSync();

        // Cancel succeeds
        Either<String, ScheduleLedger<String, TriggerSpec>> cancelResult = process.cancel("sched-4", "user-4", "No longer required", t0.plusSeconds(30)).unsafeRunSync();
        assertTrue(cancelResult.isRight());
        ScheduleLedger<String, TriggerSpec> ledger = cancelResult.getRight();

        assertEquals(ScheduleLedger.Status.CANCELLED, ledger.status());
        assertTrue(ledger.isTerminal());

        // Verify event
        List<SchedulableEvent<String, TriggerSpec>> events = publisher.getPublishedEvents();
        assertEquals(2, events.size()); // scheduled, cancelled
        assertTrue(events.get(1) instanceof OccurrenceCancelled);
    }
}