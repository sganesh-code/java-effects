package io.effects.recipes.routable;

import io.effects.recipes.routable.models.*;
import io.effects.core.Either;
import io.effects.core.IO;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoutableRecipeTest {

    // A test-specific implementation of the behavioral request interface (SupportTicket)
    static record SupportTicket(String category, String priority) implements RoutableRequest<String, String, String> {
        @Override
        public Either<String, Void> evaluateInitialRoute(RouteLedger<String, String, String> ledger, String proposedHandler, Instant now) {
            if ("HIGH".equalsIgnoreCase(priority) && "Tier-1".equalsIgnoreCase(proposedHandler)) {
                return Either.left("High priority ticket cannot be routed to Tier-1 support");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateReroute(RouteLedger<String, String, String> ledger, String currentHandler, String proposedHandler, Instant now) {
            if ("Tier-2".equalsIgnoreCase(currentHandler) && "Tier-1".equalsIgnoreCase(proposedHandler)) {
                return Either.left("Cannot downgrade handler from Tier-2 to Tier-1");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateRejection(RouteLedger<String, String, String> ledger, String reason, Instant now) {
            if ("HIGH".equalsIgnoreCase(priority) && reason.length() < 10) {
                return Either.left("Reason for rejecting high-priority ticket must be descriptive (at least 10 chars)");
            }
            return Either.right(null);
        }
    }

    @Test
    void testSuccessfulDirectRoute() {
        InMemoryStateRepository<String, RouteLedger<String, String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<RoutableEvent<String, String>> publisher = new InMemoryEventPublisher<>();
        RoutableProcess<String, String, String> process = new RoutableProcess<>(repo, publisher, new NoOpTelemetryPort());

        SupportTicket ticket = new SupportTicket("Hardware", "MEDIUM");
        process.register("ticket-101", ticket).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T10:00:00Z");

        // Execute route -> should succeed
        Either<String, RouteLedger<String, String, String>> result = process.route("ticket-101", "Tier-1", "Initial dispatch", now).unsafeRunSync();
        assertTrue(result.isRight());

        RouteLedger<String, String, String> ledger = result.getRight();
        assertEquals(RouteLedger.Status.ROUTED, ledger.status());
        assertEquals("Tier-1", ledger.currentHandler());
        assertFalse(ledger.isTerminal());

        // Verify history step
        List<RoutingStep<String, String>> history = ledger.history();
        assertEquals(1, history.size());
        assertEquals(RoutingStep.Type.ROUTE, history.get(0).type());
        assertEquals("Tier-1", history.get(0).handler());
        assertEquals("Initial dispatch", history.get(0).comment());

        // Verify published events
        List<RoutableEvent<String, String>> events = publisher.getPublishedEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof WorkRouted);
        WorkRouted<String, String> routedEvent = (WorkRouted<String, String>) events.get(0);
        assertEquals("ticket-101", routedEvent.workId());
        assertEquals("Tier-1", routedEvent.handlerId());
    }

    @Test
    void testHighPriorityRoutingValidation() {
        RoutableProcess<String, String, String> process = new RoutableProcess<>();
        SupportTicket ticket = new SupportTicket("Payment", "HIGH");
        process.register("ticket-102", ticket).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T10:05:00Z");

        // Attempt routing to Tier-1 -> should fail because of High priority validation
        Either<String, RouteLedger<String, String, String>> result = process.route("ticket-102", "Tier-1", "Dispatch to help desk", now).unsafeRunSync();
        assertTrue(result.isLeft());
        assertTrue(result.getLeft().contains("High priority ticket cannot be routed to Tier-1"));

        // Route to Tier-2 -> should succeed
        Either<String, RouteLedger<String, String, String>> successResult = process.route("ticket-102", "Tier-2", "Escalated queue", now).unsafeRunSync();
        assertTrue(successResult.isRight());
        assertEquals("Tier-2", successResult.getRight().currentHandler());
    }

    @Test
    void testSuccessfulRerouteAndValidation() {
        InMemoryStateRepository<String, RouteLedger<String, String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<RoutableEvent<String, String>> publisher = new InMemoryEventPublisher<>();
        RoutableProcess<String, String, String> process = new RoutableProcess<>(repo, publisher, new NoOpTelemetryPort());

        SupportTicket ticket = new SupportTicket("Software", "MEDIUM");
        process.register("ticket-103", ticket).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T10:10:00Z");

        // 1. Initial Route to Tier-1
        process.route("ticket-103", "Tier-1", "Standard routing", now).unsafeRunSync();

        // 2. Reroute to Tier-2 -> should succeed
        Either<String, RouteLedger<String, String, String>> rerouteResult = process.reroute("ticket-103", "Tier-2", "Needs expert advice", now.plusSeconds(10)).unsafeRunSync();
        assertTrue(rerouteResult.isRight());

        RouteLedger<String, String, String> ledger = rerouteResult.getRight();
        assertEquals("Tier-2", ledger.currentHandler());

        // Verify chronology has 2 steps (ROUTE, REROUTE)
        List<RoutingStep<String, String>> history = ledger.history();
        assertEquals(2, history.size());
        assertEquals(RoutingStep.Type.ROUTE, history.get(0).type());
        assertEquals("Tier-1", history.get(0).handler());
        assertEquals(RoutingStep.Type.REROUTE, history.get(1).type());
        assertEquals("Tier-2", history.get(1).handler());
        assertEquals("Needs expert advice", history.get(1).comment());

        // 3. Attempt to downgrade from Tier-2 back to Tier-1 -> should fail
        Either<String, RouteLedger<String, String, String>> badReroute = process.reroute("ticket-103", "Tier-1", "Downgrade task", now.plusSeconds(20)).unsafeRunSync();
        assertTrue(badReroute.isLeft());
        assertTrue(badReroute.getLeft().contains("Cannot downgrade handler"));

        // Verify events emitted
        List<RoutableEvent<String, String>> events = publisher.getPublishedEvents();
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof WorkRouted);
        assertTrue(events.get(1) instanceof WorkRerouted);

        WorkRerouted<String, String> reroutedEvent = (WorkRerouted<String, String>) events.get(1);
        assertEquals("Tier-1", reroutedEvent.previousHandlerId());
        assertEquals("Tier-2", reroutedEvent.newHandlerId());
    }

    @Test
    void testRejectionAndValidation() {
        RoutableProcess<String, String, String> process = new RoutableProcess<>();
        SupportTicket ticket = new SupportTicket("Security", "HIGH");
        process.register("ticket-104", ticket).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T10:15:00Z");

        // 1. Attempt to reject with too short of a comment -> should fail
        Either<String, RouteLedger<String, String, String>> badReject = process.reject("ticket-104", "No spam", now).unsafeRunSync();
        assertTrue(badReject.isLeft());
        assertTrue(badReject.getLeft().contains("Reason for rejecting high-priority ticket must be descriptive"));

        // 2. Reject with long comment -> should succeed
        Either<String, RouteLedger<String, String, String>> rejectResult = process.reject("ticket-104", "This ticket is spam or malicious request from outside", now.plusSeconds(10)).unsafeRunSync();
        assertTrue(rejectResult.isRight());

        RouteLedger<String, String, String> ledger = rejectResult.getRight();
        assertEquals(RouteLedger.Status.REJECTED, ledger.status());
        assertNull(ledger.currentHandler());
        assertTrue(ledger.isTerminal());

        // 3. Cannot route/reroute a terminal/rejected route
        Either<String, RouteLedger<String, String, String>> routeAfterReject = process.route("ticket-104", "Tier-2", "Retry routing", now.plusSeconds(20)).unsafeRunSync();
        assertTrue(routeAfterReject.isLeft());
        assertTrue(routeAfterReject.getLeft().contains("Work has already been routed"));
    }

    @Test
    void testRejectionIdempotency() {
        RoutableProcess<String, String, String> process = new RoutableProcess<>();
        SupportTicket ticket = new SupportTicket("Hardware", "MEDIUM");
        process.register("ticket-105", ticket).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T10:20:00Z");

        // Reject first time
        Either<String, RouteLedger<String, String, String>> reject1 = process.reject("ticket-105", "Duplicate report", now).unsafeRunSync();
        assertTrue(reject1.isRight());
        assertEquals(RouteLedger.Status.REJECTED, reject1.getRight().status());

        // Reject second time -> idempotent success
        Either<String, RouteLedger<String, String, String>> reject2 = process.reject("ticket-105", "Duplicate report second try", now.plusSeconds(10)).unsafeRunSync();
        assertTrue(reject2.isRight());
        assertEquals(RouteLedger.Status.REJECTED, reject2.getRight().status());
    }
}
