package io.effects.recipes.routable;

import io.effects.recipes.routable.models.*;
import io.effects.core.Either;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing a request or unit of work
 * that can be routed, rerouted, or rejected.
 *
 * It contains zero passive getters/setters and executes purely and synchronously.
 */
public interface RoutableRequest<ID, H, C> {

    /**
     * Evaluates if the work can be routed to a proposed initial handler.
     */
    Either<String, Void> evaluateInitialRoute(RouteLedger<ID, H, C> ledger, H proposedHandler, Instant now);

    /**
     * Evaluates if the work can be rerouted from the current handler to a new proposed handler.
     */
    Either<String, Void> evaluateReroute(RouteLedger<ID, H, C> ledger, H currentHandler, H proposedHandler, Instant now);

    /**
     * Evaluates if the work routing can be rejected.
     */
    Either<String, Void> evaluateRejection(RouteLedger<ID, H, C> ledger, C reason, Instant now);
}
