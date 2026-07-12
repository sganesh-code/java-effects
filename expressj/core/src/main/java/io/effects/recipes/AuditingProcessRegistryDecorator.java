package io.effects.recipes;

import io.effects.IO;
import java.util.Objects;

/**
 * A reusable, cross-cutting decorator that intercepts ProcessRegistry lifecycle 
 * registrations and logs compliance auditing logs. 
 * 
 * Demonstrates the power of the ProcessRegistry abstract contract to easily attach 
 * generic middleware/interceptors across any B2B process in the library.
 *
 * @param <ID> the unique identifier type for the transaction
 * @param <R> the contract interface representing the participating behavioral domain object
 */
public final class AuditingProcessRegistryDecorator<ID, R> implements ProcessRegistry<ID, R> {
    private final ProcessRegistry<ID, R> delegate;
    private final String processName;

    /**
     * Constructs a decorator wrapping an underlying process registry.
     */
    public AuditingProcessRegistryDecorator(ProcessRegistry<ID, R> delegate, String processName) {
        this.delegate = Objects.requireNonNull(delegate);
        this.processName = Objects.requireNonNull(processName);
    }

    @Override
    public IO<Void> register(ID transactionId, R behavioralObject) {
        return IO.delay(() -> {
            // Business auditing log trace
            System.out.println("[AUDIT] [REGISTRY] [INTERCEPT] Registering behavioral object of class [" 
                + behavioralObject.getClass().getSimpleName() + "] for transaction ID [" 
                + transactionId + "] on [" + processName + "] process.");
            return null;
        }).flatMap(v -> delegate.register(transactionId, behavioralObject));
    }

    @Override
    public IO<Void> unregister(ID transactionId) {
        return IO.delay(() -> {
            System.out.println("[AUDIT] [REGISTRY] [INTERCEPT] Evicting registration for transaction ID [" 
                + transactionId + "] on [" + processName + "] process.");
            return null;
        }).flatMap(v -> delegate.unregister(transactionId));
    }

    @Override
    public IO<Boolean> isRegistered(ID transactionId) {
        return delegate.isRegistered(transactionId);
    }
}
