package io.effects.recipes.reservable;

import io.effects.IO;
import java.util.Optional;

/**
 * A Port (Interface) representing the storage capability for reservation recipe states.
 * All operations return lazy monadic IO blocks to protect effect-boundaries.
 */
public interface StateRepository {

    /**
     * Persists the given ResourceLedger under the specified resourceId.
     */
    IO<Void> saveLedger(String resourceId, ResourceLedger ledger);

    /**
     * Retrieves the ResourceLedger associated with the specified resourceId, if it exists.
     */
    IO<Optional<ResourceLedger>> findLedger(String resourceId);

    /**
     * Persists a Hold, mapping it both by its holdId and to the specified resourceId.
     */
    IO<Void> saveHold(Hold hold, String resourceId);

    /**
     * Retrieves the Hold associated with the specified holdId, if it exists.
     */
    IO<Optional<Hold>> findHold(String holdId);

    /**
     * Retrieves the resourceId associated with the specified holdId, if mapped.
     */
    IO<Optional<String>> findResourceIdForHold(String holdId);

    /**
     * Removes the Hold and its mapping by the specified holdId.
     */
    IO<Void> removeHold(String holdId);
}
