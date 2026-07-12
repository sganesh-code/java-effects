package io.effects.recipes;

import io.effects.core.IO;

/**
 * A unified contract representing a business-facing Recipe (Process Manager) 
 * that coordinates the active registration, unregistration, and state verification 
 * of participating behavioral domain objects.
 *
 * @param <ID> the unique identifier type for the transaction or entity
 * @param <R> the contract interface representing the participating behavioral domain object
 */
public interface Recipe<ID, R> {

    /**
     * Registers a behavioral business domain object to participate in this Recipe's active lifecycle.
     *
     * @param transactionId the unique identifier for the transaction
     * @param behavioralObject the domain object containing custom business rules and callbacks
     * @return an IO computation executing the registration
     */
    IO<Void> register(ID transactionId, R behavioralObject);

    /**
     * Unregisters/evicts a behavioral business domain object from active memory registration.
     *
     * @param transactionId the unique identifier for the transaction
     * @return an IO computation executing the unregistration
     */
    IO<Void> unregister(ID transactionId);

    /**
     * Verifies if a behavioral business domain object is currently registered for the given identifier.
     *
     * @param transactionId the unique identifier for the transaction
     * @return an IO computation returning true if registered, false otherwise
     */
    IO<Boolean> isRegistered(ID transactionId);
}
