package io.effects.recipes.ports.approvable;

import io.effects.IO;
import io.effects.recipes.approvable.ApprovalRecord;
import java.util.Optional;

/**
 * Persistence port for retrieving and saving the state of approval records.
 */
public interface ApprovalStateRepository {

    /**
     * Retrieve the current approval record for a request.
     */
    IO<Optional<ApprovalRecord>> findRecord(String requestId);

    /**
     * Save/persist the approval record.
     */
    IO<Void> saveRecord(ApprovalRecord record);
}
