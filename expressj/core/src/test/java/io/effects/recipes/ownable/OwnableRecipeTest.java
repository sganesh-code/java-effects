package io.effects.recipes.ownable;

import io.effects.recipes.ownable.models.*;

import io.effects.core.Either;
import io.effects.core.IO;
import io.effects.ports.TelemetryPort;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OwnableRecipeTest {

    // A custom, clean, non-anemic domain representation of an Owner context.
    private record OwnerPrincipal(String name) {
        public OwnerPrincipal {
            java.util.Objects.requireNonNull(name);
        }
    }

    // A concrete, behavioral domain asset representing a digital property (e.g. source code repo or license)
    // Exposes NO getter APIs for identity or initiators, satisfying our pure OOP guidelines.
    private record DigitalAsset(boolean restrictionEnabled) implements OwnableRequest<String, OwnerPrincipal> {

        @Override
        public Either<String, Void> evaluateInitialAssignment(OwnerPrincipal owner, Instant now) {
            if (owner == null || owner.name().isBlank()) {
                return Either.left("Owner ID cannot be empty");
            }
            if (restrictionEnabled && owner.name().equalsIgnoreCase("restricted-user")) {
                return Either.left("Cannot assign ownership to restricted-user");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateTransfer(
                OwnershipRecord<String, OwnerPrincipal> record,
                OwnerPrincipal currentOwner,
                OwnerPrincipal proposedOwner,
                OwnerPrincipal actor,
                Instant now
        ) {
            // Under revocation, proposedOwner is null
            if (proposedOwner == null) {
                // Revocation rules
                if (!actor.equals(currentOwner) && !actor.name().equals("SYSTEM_ADMIN")) {
                    return Either.left("Only the current owner or SYSTEM_ADMIN can revoke ownership");
                }
                return Either.right(null);
            }

            // Transfer rules
            if (!actor.equals(currentOwner)) {
                return Either.left("Only the current owner can initiate a transfer");
            }
            if (proposedOwner.equals(currentOwner)) {
                return Either.left("Cannot transfer to the same owner");
            }
            if (restrictionEnabled && proposedOwner.name().equalsIgnoreCase("restricted-user")) {
                return Either.left("Cannot transfer ownership to restricted-user");
            }
            return Either.right(null);
        }
    }

    // 1. Initial Assignment & Double Assignment Invariant
    @Test
    void testInitialOwnershipAssignment() {
        OwnableProcess<String, OwnerPrincipal> process = new OwnableProcess<>();
        DigitalAsset repo = new DigitalAsset(true);
        process.register("repo-1", repo).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T10:00:00Z");

        // Fails because name is empty
        Either<String, OwnershipRecord<String, OwnerPrincipal>> badResult = process.assignOwner("repo-1", new OwnerPrincipal(""), t0).unsafeRunSync();
        assertTrue(badResult.isLeft());

        // Fails because restricted user
        Either<String, OwnershipRecord<String, OwnerPrincipal>> restrictedResult = process.assignOwner("repo-1", new OwnerPrincipal("restricted-user"), t0).unsafeRunSync();
        assertTrue(restrictedResult.isLeft());

        // Succeeds
        Either<String, OwnershipRecord<String, OwnerPrincipal>> successResult = process.assignOwner("repo-1", new OwnerPrincipal("alice"), t0).unsafeRunSync();
        assertTrue(successResult.isRight());
        OwnershipRecord<String, OwnerPrincipal> ownershipRecord = successResult.getRight();

        assertEquals(new OwnerPrincipal("alice"), ownershipRecord.currentOwner());
        assertTrue(ownershipRecord.hasOwner());

        // Chronology contains exactly the assignment step
        List<OwnershipStep<OwnerPrincipal>> history = ownershipRecord.history();
        assertEquals(1, history.size());
        assertEquals(new OwnerPrincipal("alice"), history.get(0).owner());
        assertEquals(OwnershipStep.Type.ASSIGN, history.get(0).type());

        // Double assign fails
        Either<String, OwnershipRecord<String, OwnerPrincipal>> reAssign = process.assignOwner("repo-1", new OwnerPrincipal("bob"), t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(reAssign.isLeft());
        assertTrue(reAssign.getLeft().contains("Asset already has an active owner"));
    }

    // 2. Ownership Transfer & Actor Validation Invariant
    @Test
    void testOwnershipTransfer() {
        InMemoryStateRepository<String, OwnershipRecord<String, OwnerPrincipal>> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<OwnershipEvent<String, OwnerPrincipal>> publisher = new InMemoryEventPublisher<>();
        OwnableProcess<String, OwnerPrincipal> process = new OwnableProcess<>(repository, publisher, new NoOpTelemetryPort());

        DigitalAsset document = new DigitalAsset(false);
        process.register("doc-1", document).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T11:00:00Z");

        // Set initial owner
        process.assignOwner("doc-1", new OwnerPrincipal("alice"), t0).unsafeRunSync();

        // Bob tries to transfer it -> Fails (only current owner can transfer)
        Either<String, OwnershipRecord<String, OwnerPrincipal>> badTransfer = process.transferOwner(
                "doc-1", new OwnerPrincipal("alice"), new OwnerPrincipal("charlie"), new OwnerPrincipal("bob"), "Stealing document", t0.plusSeconds(10)
        ).unsafeRunSync();
        assertTrue(badTransfer.isLeft());
        assertTrue(badTransfer.getLeft().contains("Only the current owner can initiate a transfer"));

        // Alice transfers to Bob -> Succeeds
        Either<String, OwnershipRecord<String, OwnerPrincipal>> goodTransfer = process.transferOwner(
                "doc-1", new OwnerPrincipal("alice"), new OwnerPrincipal("bob"), new OwnerPrincipal("alice"), "Gift to Bob", t0.plusSeconds(20)
        ).unsafeRunSync();
        assertTrue(goodTransfer.isRight());
        OwnershipRecord<String, OwnerPrincipal> record = goodTransfer.getRight();

        assertEquals(new OwnerPrincipal("bob"), record.currentOwner());

        // History consists of both assign and transfer steps
        List<OwnershipStep<OwnerPrincipal>> history = record.history();
        assertEquals(2, history.size());
        assertEquals(OwnershipStep.Type.ASSIGN, history.get(0).type());
        assertEquals(OwnershipStep.Type.TRANSFER, history.get(1).type());
        assertEquals(new OwnerPrincipal("alice"), history.get(1).owner());
        assertEquals("Gift to Bob", history.get(1).comment());

        // Verify emitted events
        List<OwnershipEvent<String, OwnerPrincipal>> events = publisher.getPublishedEvents();
        assertEquals(2, events.size());
        assertInstanceOf(OwnershipAssigned.class, events.get(0));
        assertInstanceOf(OwnershipTransferred.class, events.get(1));

        OwnershipTransferred<String, OwnerPrincipal> transferEvent = (OwnershipTransferred<String, OwnerPrincipal>) events.get(1);
        assertEquals("doc-1", transferEvent.assetId());
        assertEquals(new OwnerPrincipal("alice"), transferEvent.previousOwnerId());
        assertEquals(new OwnerPrincipal("bob"), transferEvent.newOwnerId());
    }

    // 3. Ownership Revocation Invariant
    @Test
    void testOwnershipRevocation() {
        InMemoryStateRepository<String, OwnershipRecord<String, OwnerPrincipal>> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<OwnershipEvent<String, OwnerPrincipal>> publisher = new InMemoryEventPublisher<>();
        OwnableProcess<String, OwnerPrincipal> process = new OwnableProcess<>(repository, publisher, new NoOpTelemetryPort());

        DigitalAsset patent = new DigitalAsset(false);
        process.register("patent-1", patent).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T12:00:00Z");

        process.assignOwner("patent-1", new OwnerPrincipal("alice"), t0).unsafeRunSync();

        // Bob attempts to revoke -> Fails (insufficient privileges)
        Either<String, OwnershipRecord<String, OwnerPrincipal>> badRevoke = process.revokeOwner(
                "patent-1", new OwnerPrincipal("alice"), new OwnerPrincipal("bob"), "General complaint", t0.plusSeconds(10)
        ).unsafeRunSync();
        assertTrue(badRevoke.isLeft());

        // System Admin revokes -> Succeeds
        Either<String, OwnershipRecord<String, OwnerPrincipal>> goodRevoke = process.revokeOwner(
                "patent-1", new OwnerPrincipal("alice"), new OwnerPrincipal("SYSTEM_ADMIN"), "Patent expired", t0.plusSeconds(20)
        ).unsafeRunSync();
        assertTrue(goodRevoke.isRight());
        OwnershipRecord<String, OwnerPrincipal> record = goodRevoke.getRight();

        assertNull(record.currentOwner());
        assertFalse(record.hasOwner());

        // Verify history
        List<OwnershipStep<OwnerPrincipal>> history = record.history();
        assertEquals(2, history.size());
        assertEquals(OwnershipStep.Type.REVOKE, history.get(1).type());
        assertEquals(new OwnerPrincipal("SYSTEM_ADMIN"), history.get(1).owner());

        // Verify events
        List<OwnershipEvent<String, OwnerPrincipal>> events = publisher.getPublishedEvents();
        assertEquals(2, events.size());
        assertInstanceOf(OwnershipRevoked.class, events.get(1));

        OwnershipRevoked<String, OwnerPrincipal> revokeEvent = (OwnershipRevoked<String, OwnerPrincipal>) events.get(1);
        assertEquals("patent-1", revokeEvent.assetId());
        assertEquals(new OwnerPrincipal("alice"), revokeEvent.previousOwnerId());

        // Trying to transfer a revoked/ownerless asset is prohibited
        Either<String, OwnershipRecord<String, OwnerPrincipal>> badTransfer = process.transferOwner(
                "patent-1", new OwnerPrincipal("alice"), new OwnerPrincipal("bob"), new OwnerPrincipal("alice"), "Try transfer revoked", t0.plusSeconds(30)
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
        OwnableProcess<String, OwnerPrincipal> process = new OwnableProcess<>(
                new InMemoryStateRepository<>(),
                new InMemoryEventPublisher<>(),
                spy
        );

        DigitalAsset token = new DigitalAsset(false);
        process.register("token-1", token).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T13:00:00Z");

        process.assignOwner("token-1", new OwnerPrincipal("alice"), t0).unsafeRunSync();

        assertEquals(1, spy.successes);
        assertEquals(1, spy.durations);
    }
}