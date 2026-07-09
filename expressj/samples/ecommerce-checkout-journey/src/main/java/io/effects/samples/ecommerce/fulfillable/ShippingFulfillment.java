package io.effects.samples.ecommerce.fulfillable;

import io.effects.Either;
import io.effects.recipes.fulfillable.FulfillableRequest;
import io.effects.recipes.fulfillable.FulfillmentLedger;
import java.time.Instant;

public class ShippingFulfillment implements FulfillableRequest<String, Integer> {

    @Override
    public Either<String, Void> evaluateAllocation(FulfillmentLedger<String, Integer> ledger, Integer quantity, Instant now) {
        if (quantity <= 0) {
            return Either.left("Allocation quantity must be positive.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluatePackaging(FulfillmentLedger<String, Integer> ledger, Integer quantity, Instant now) {
        if (ledger.status() != FulfillmentLedger.Status.ALLOCATING) {
            return Either.left("Items must be allocated before they can be packaged.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateDispatch(FulfillmentLedger<String, Integer> ledger, Instant now) {
        if (ledger.status() != FulfillmentLedger.Status.PACKAGING) {
            return Either.left("Items must be packaged before they can be dispatched.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateCompletion(FulfillmentLedger<String, Integer> ledger, Instant now) {
        if (ledger.status() != FulfillmentLedger.Status.DISPATCHED) {
            return Either.left("Fulfillment must be dispatched before it can be completed.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, FulfillmentLedger.Status> evaluateRelease(FulfillmentLedger<String, Integer> ledger, Integer quantity, Instant now) {
        if (ledger.status() == FulfillmentLedger.Status.DISPATCHED || ledger.status() == FulfillmentLedger.Status.COMPLETED) {
            return Either.left("Cannot release items once shipped or completed.");
        }
        return Either.right(FulfillmentLedger.Status.INITIAL);
    }
}
