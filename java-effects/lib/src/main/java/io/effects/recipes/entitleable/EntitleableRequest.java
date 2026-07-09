package io.effects.recipes.entitleable;

import io.effects.Either;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing an entitlement rule context.
 * It contains NO passive getters, relying entirely on double-dispatch / object collaboration.
 */
public interface EntitleableRequest<ID, G, C> {

    /**
     * Behavioral Message: Evaluates whether a proposed entitlement grant is allowed.
     */
    Either<String, Void> evaluateGrant(EntitlementLedger<ID, G> ledger, G grant, Instant now);

    /**
     * Behavioral Message: Evaluates whether a proposed access check is allowed.
     */
    Either<String, Void> evaluateCheck(EntitlementLedger<ID, G> ledger, G grant, C context, Instant now);
}