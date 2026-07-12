package io.effects.recipes;

import io.effects.core.IO;
import java.util.Objects;

/**
 * A reusable, cross-cutting decorator that intercepts Recipe lifecycle 
 * registrations and logs compliance auditing logs. 
 *
 * @param <ID> the unique identifier type for the transaction
 * @param <R> the contract interface representing the participating behavioral domain object
 */
public final class AuditingRecipeDecorator<ID, R> implements Recipe<ID, R> {
    private final Recipe<ID, R> delegate;
    private final String recipeName;

    /**
     * Constructs an auditing decorator wrapping an underlying Recipe.
     */
    public AuditingRecipeDecorator(Recipe<ID, R> delegate, String recipeName) {
        this.delegate = Objects.requireNonNull(delegate);
        this.recipeName = Objects.requireNonNull(recipeName);
    }

    @Override
    public IO<Void> register(ID transactionId, R behavioralObject) {
        return IO.delay(() -> {
            System.out.println("[AUDIT] [RECIPE] [INTERCEPT] Registering behavioral object of class [" 
                + behavioralObject.getClass().getSimpleName() + "] for transaction ID [" 
                + transactionId + "] on [" + recipeName + "] recipe.");
            return null;
        }).flatMap(v -> delegate.register(transactionId, behavioralObject));
    }

    @Override
    public IO<Void> unregister(ID transactionId) {
        return IO.delay(() -> {
            System.out.println("[AUDIT] [RECIPE] [INTERCEPT] Evicting registration for transaction ID [" 
                + transactionId + "] on [" + recipeName + "] recipe.");
            return null;
        }).flatMap(v -> delegate.unregister(transactionId));
    }

    @Override
    public IO<Boolean> isRegistered(ID transactionId) {
        return delegate.isRegistered(transactionId);
    }
}
