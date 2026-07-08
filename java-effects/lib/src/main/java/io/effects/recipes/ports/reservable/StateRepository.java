package io.effects.recipes.ports.reservable;

import io.effects.IO;
import io.effects.recipes.reservable.Hold;
import io.effects.recipes.reservable.ResourceLedger;
import java.util.Optional;

/**
 * Persistence port for saving and loading resource ledger states and active holds.
 */
public interface StateRepository {

    IO<Void> saveLedger(String resourceId, ResourceLedger ledger);

    IO<Optional<ResourceLedger>> findLedger(String resourceId);

    IO<Void> saveHold(Hold hold, String resourceId);

    IO<Optional<Hold>> findHold(String holdId);

    IO<Optional<String>> findResourceIdForHold(String holdId);

    IO<Void> removeHold(String holdId);
}
