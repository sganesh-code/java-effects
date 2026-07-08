package io.effects.recipes.reservable;

import io.effects.IO;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An in-memory, thread-safe implementation of StateRepository for testing and local use.
 */
final class InMemoryStateRepository implements StateRepository {
    private final ConcurrentMap<String, ResourceLedger> ledgers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Hold> holds = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> holdToResourceMapping = new ConcurrentHashMap<>();

    @Override
    public IO<Void> saveLedger(String resourceId, ResourceLedger ledger) {
        return IO.delay(() -> {
            ledgers.put(resourceId, ledger);
            return null;
        });
    }

    @Override
    public IO<Optional<ResourceLedger>> findLedger(String resourceId) {
        return IO.delay(() -> Optional.ofNullable(ledgers.get(resourceId)));
    }

    @Override
    public IO<Void> saveHold(Hold hold, String resourceId) {
        return IO.delay(() -> {
            holds.put(hold.holdId(), hold);
            holdToResourceMapping.put(hold.holdId(), resourceId);
            return null;
        });
    }

    @Override
    public IO<Optional<Hold>> findHold(String holdId) {
        return IO.delay(() -> Optional.ofNullable(holds.get(holdId)));
    }

    @Override
    public IO<Optional<String>> findResourceIdForHold(String holdId) {
        return IO.delay(() -> Optional.ofNullable(holdToResourceMapping.get(holdId)));
    }

    @Override
    public IO<Void> removeHold(String holdId) {
        return IO.delay(() -> {
            holds.remove(holdId);
            holdToResourceMapping.remove(holdId);
            return null;
        });
    }
}
