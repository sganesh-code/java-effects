package io.effects.recipes.approvable;

import io.effects.recipes.approvable.models.*;

/**
 * States representing the lifecycle of an approval request.
 */
public enum Status {
    PENDING,
    APPROVED,
    REJECTED,
    ESCALATED
}
