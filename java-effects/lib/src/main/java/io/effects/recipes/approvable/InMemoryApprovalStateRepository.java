package io.effects.recipes.approvable;

import io.effects.IO;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An in-memory, thread-safe implementation of ApprovalStateRepository for testing and local use.
 */
final class InMemoryApprovalStateRepository implements ApprovalStateRepository {
    private final ConcurrentMap<String, ApprovalRecord> records = new ConcurrentHashMap<>();

    @Override
    public IO<Optional<ApprovalRecord>> findRecord(String requestId) {
        return IO.delay(() -> Optional.ofNullable(records.get(requestId)));
    }

    @Override
    public IO<Void> saveRecord(ApprovalRecord record) {
        return IO.delay(() -> {
            records.put(record.requestId(), record);
            return null;
        });
    }
}
