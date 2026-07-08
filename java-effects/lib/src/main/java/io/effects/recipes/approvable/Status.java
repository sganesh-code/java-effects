package io.effects.recipes.approvable;

/**
 * States representing the lifecycle of an approval request.
 */
public enum Status {
    PENDING,
    APPROVED,
    REJECTED,
    ESCALATED
}
