package io.effects.samples.ecommerce.ownable;

import io.effects.Either;
import io.effects.recipes.ownable.OwnableRequest;
import io.effects.recipes.ownable.OwnershipRecord;
import java.time.Instant;

public class AssetOwnership implements OwnableRequest<String, String> {

    @Override
    public Either<String, Void> evaluateInitialAssignment(String owner, Instant now) {
        if (owner == null || owner.isBlank()) {
            return Either.left("Initial owner must be specified.");
        }
        if (!owner.contains("@")) {
            return Either.left("Owner identifier must be a valid email address.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateTransfer(OwnershipRecord<String, String> record, String currentOwner, String proposedOwner, String actor, Instant now) {
        if (!currentOwner.equals(actor)) {
            return Either.left("Only the current owner (" + currentOwner + ") can initiate an ownership transfer.");
        }
        if (proposedOwner == null || proposedOwner.isBlank() || !proposedOwner.contains("@")) {
            return Either.left("Proposed owner must be a valid email address.");
        }
        return Either.right(null);
    }
}
