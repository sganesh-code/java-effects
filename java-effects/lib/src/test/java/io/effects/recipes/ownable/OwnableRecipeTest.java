package io.effects.recipes.ownable;

import io.effects.Either;
import io.effects.ForIO;
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

class OwnableRecipeTest {

    // A concrete, behavioral domain asset representing a digital property (e.g. source code repo or license)
    // Exposes NO getter APIs for identity or initiators, satisfying our pure OOP guidelines.
    private record DigitalAsset(boolean restrictionEnabled) implements OwnableRequest {

        @Override
        public Either<String, Void> evaluateInitialAssignment(String ownerId, Instant now) {
            if (ownerId == null || ownerId.isBlank()) {
                return Either.left("Owner ID cannot be empty");
            }
            if (restrictionEnabled && ownerId.equalsIgnoreCase("restricted-user")) {
                return Either.left("Cannot assign ownership to restricted-user");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateTransfer(
                OwnershipRecord record,
                String currentOwnerId,
                String proposedOwnerId,
                String actorId,
                Instant now
        ) {
            // Under revocation, proposedOwnerId is null
            if (proposedOwnerId == null) {
                // Revocation rules
                if (!actorId.equals(currentOwnerId) && !actorId.equals("SYSTEM_ADMIN")) {
                    return Either.left("Only the current owner or SYSTEM_ADMIN can revoke ownership");
                }
                return Either.right(null);
            }

            // Transfer rules
            if (!actorId.equals(currentOwnerId)) {
                return Either.left("Only the current owner can initiate a transfer");
            }
            if (proposedOwnerId.equalsIgnoreCase(currentOwnerId)) {
                return Either.left("Cannot transfer to the same owner");
            }
            if (restrictionEnabled && proposedOwnerId.equalsIgnoreCase("restricted-user")) {
                return Either.left("Cannot transfer ownership to restricted-user");
            }
            return Either.right(null);
        }
    }

    // 1. Initial Assignment & Double Assignment Invariant
    @Test
    void testInitialOwnershipAssignment() {
        OwnableProcess process = new OwnableProcess();
        DigitalAsset repo = new DigitalAsset(true);
        ForIO.set(process.register("repo-1", repo))
                .bind(ignored -> IO.of(Instant.parse("2026-07-08T10:00:00Z")))
                .bind((ignored, t0) ->
                        process.assignOwner("repo-1", "", t0))
                .bind((ignored, t0, badResult) ->
                        process.assignOwner("repo-1", "restricted-user", t0))
                .bind((ignored, t0, badResult, restrictedResult) ->
                        process.assignOwner("repo-1", "alice", t0))
                .yield((ignored, t0, badResult, restrictedResult, successResult) -> {
                    assertTrue(successResult.isRight());
                    OwnershipRecord ownershipRecord = successResult.getRight();

                    assertEquals("alice", ownershipRecord.currentOwnerId());
                    assertTrue(ownershipRecord.hasOwner());

                    // Chronology contains exactly the assignment step
                    List<OwnershipStep> history = ownershipRecord.history();
                    assertEquals(1, history.size());
                    assertEquals("alice", history.get(0).actorId());
                    assertEquals(OwnershipStep.Type.ASSIGN, history.get(0).type());

                    // Double assign fails
                    Either<String, OwnershipRecord> reAssign = process.assignOwner("repo-1", "bob", t0.plusSeconds(10)).unsafeRunSync();
                    assertTrue(reAssign.isLeft());
                    assertTrue(reAssign.getLeft().contains("Asset already has an active owner"));
                    return null;
                });
    }

    // 2. Ownership Transfer & Actor Validation Invariant
    @Test
    void testOwnershipTransfer() {
        InMemoryStateRepository<String, OwnershipRecord> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<OwnershipEvent> publisher = new InMemoryEventPublisher<>();
        OwnableProcess process = new OwnableProcess(repository, publisher, new NoOpTelemetryPort());

        DigitalAsset document = new DigitalAsset(false);
        process.register("doc-1", document).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T11:00:00Z");

        // Set initial owner
        process.assignOwner("doc-1", "alice", t0).unsafeRunSync();

        // Bob tries to transfer it -> Fails (only current owner can transfer)
        Either<String, OwnershipRecord> badTransfer = process.transferOwner(
                "doc-1", "alice", "charlie", "bob", "Stealing document", t0.plusSeconds(10)
        ).unsafeRunSync();
        assertTrue(badTransfer.isLeft());
        assertTrue(badTransfer.getLeft().contains("Only the current owner can initiate a transfer"));

        // Alice transfers to Bob -> Succeeds
        Either<String, OwnershipRecord> goodTransfer = process.transferOwner(
                "doc-1", "alice", "bob", "alice", "Gift to Bob", t0.plusSeconds(20)
        ).unsafeRunSync();
        assertTrue(goodTransfer.isRight());
        OwnershipRecord record = goodTransfer.getRight();

        assertEquals("bob", record.currentOwnerId());

        // History consists of both assign and transfer steps
        List<OwnershipStep> history = record.history();
        assertEquals(2, history.size());
        assertEquals(OwnershipStep.Type.ASSIGN, history.get(0).type());
        assertEquals(OwnershipStep.Type.TRANSFER, history.get(1).type());
        assertEquals("alice", history.get(1).actorId());
        assertEquals("Gift to Bob", history.get(1).comment());

        // Verify emitted events
        List<OwnershipEvent> events = publisher.getPublishedEvents();
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof OwnershipAssigned);
        assertTrue(events.get(1) instanceof OwnershipTransferred);

        OwnershipTransferred transferEvent = (OwnershipTransferred) events.get(1);
        assertEquals("doc-1", transferEvent.assetId());
        assertEquals("alice", transferEvent.previousOwnerId());
        assertEquals("bob", transferEvent.newOwnerId());
    }

    // 3. Ownership Revocation Invariant
    @Test
    void testOwnershipRevocation() {
        InMemoryStateRepository<String, OwnershipRecord> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<OwnershipEvent> publisher = new InMemoryEventPublisher<>();
        OwnableProcess process = new OwnableProcess(repository, publisher, new NoOpTelemetryPort());

        DigitalAsset patent = new DigitalAsset(false);
        process.register("patent-1", patent).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T12:00:00Z");

        process.assignOwner("patent-1", "alice", t0).unsafeRunSync();

        // Bob attempts to revoke -> Fails (insufficient privileges)
        Either<String, OwnershipRecord> badRevoke = process.revokeOwner(
                "patent-1", "alice", "bob", "General complaint", t0.plusSeconds(10)
        ).unsafeRunSync();
        assertTrue(badRevoke.isLeft());

        // System Admin revokes -> Succeeds
        Either<String, OwnershipRecord> goodRevoke = process.revokeOwner(
                "patent-1", "alice", "SYSTEM_ADMIN", "Patent expired", t0.plusSeconds(20)
        ).unsafeRunSync();
        assertTrue(goodRevoke.isRight());
        OwnershipRecord record = goodRevoke.getRight();

        assertNull(record.currentOwnerId());
        assertFalse(record.hasOwner());

        // Verify history
        List<OwnershipStep> history = record.history();
        assertEquals(2, history.size());
        assertEquals(OwnershipStep.Type.REVOKE, history.get(1).type());
        assertEquals("SYSTEM_ADMIN", history.get(1).actorId());

        // Verify events
        List<OwnershipEvent> events = publisher.getPublishedEvents();
        assertEquals(2, events.size());
        assertTrue(events.get(1) instanceof OwnershipRevoked);

        OwnershipRevoked revokeEvent = (OwnershipRevoked) events.get(1);
        assertEquals("patent-1", revokeEvent.assetId());
        assertEquals("alice", revokeEvent.previousOwnerId());

        // Trying to transfer a revoked/ownerless asset is prohibited
        Either<String, OwnershipRecord> badTransfer = process.transferOwner(
                "patent-1", "alice", "bob", "alice", "Try transfer revoked", t0.plusSeconds(30)
        ).unsafeRunSync();
        assertTrue(badTransfer.isLeft());
        assertTrue(badTransfer.getLeft().contains("Cannot transfer: asset has no active owner"));
    }

    // 4. Telemetry and Dependency Injection Instrumentation
    @Test
    void testOwnableTelemetrySpy() {
        class TelemetrySpy implements TelemetryPort {
            int successes = 0;
            int durations = 0;

            @Override
            public IO<Void> recordSuccess(String context, String operationId) {
                return IO.delay(() -> {
                    successes++;
                    return null;
                });
            }

            @Override
            public IO<Void> recordFailure(String context, String operationId, String reason) {
                return IO.of(null);
            }

            @Override
            public IO<Void> recordDuration(String context, String operationId, long durationMs) {
                return IO.delay(() -> {
                    durations++;
                    return null;
                });
            }
        }

        TelemetrySpy spy = new TelemetrySpy();
        OwnableProcess process = new OwnableProcess(
                new InMemoryStateRepository<>(),
                new InMemoryEventPublisher<>(),
                spy
        );

        DigitalAsset token = new DigitalAsset(false);
        process.register("token-1", token).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T13:00:00Z");

        process.assignOwner("token-1", "alice", t0).unsafeRunSync();

        assertEquals(1, spy.successes);
        assertEquals(1, spy.durations);
    }
}
