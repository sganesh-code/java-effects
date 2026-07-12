package io.effects.recipes;

import io.effects.IO;
import io.effects.recipes.ownable.OwnableProcess;
import io.effects.recipes.ownable.OwnableRequest;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import static org.junit.jupiter.api.Assertions.*;

class ProcessRegistryTest {

    // Simple behavioral request stub
    private static class DummyOwnableRequest implements OwnableRequest<String, String> {
        @Override
        public io.effects.Either<String, Void> evaluateInitialAssignment(String owner, java.time.Instant now) {
            return io.effects.Either.right(null);
        }

        @Override
        public io.effects.Either<String, Void> evaluateTransfer(
            io.effects.recipes.ownable.OwnershipRecord<String, String> record, 
            String currentOwner, 
            String proposedOwner, 
            String actor, 
            java.time.Instant now
        ) {
            return io.effects.Either.right(null);
        }
    }

    @Test
    void testStandardProcessRegistryOperations() {
        ProcessRegistry<String, OwnableRequest<String, String>> process = new OwnableProcess<>();
        DummyOwnableRequest asset = new DummyOwnableRequest();

        // 1. Verify initially not registered
        assertFalse(process.isRegistered("asset-101").unsafeRunSync());

        // 2. Register
        process.register("asset-101", asset).unsafeRunSync();

        // 3. Verify registered
        assertTrue(process.isRegistered("asset-101").unsafeRunSync());

        // 4. Unregister
        process.unregister("asset-101").unsafeRunSync();

        // 5. Verify no longer registered
        assertFalse(process.isRegistered("asset-101").unsafeRunSync());
    }

    @Test
    void testAuditingProcessRegistryDecorator() {
        OwnableProcess<String, String> process = new OwnableProcess<>();
        ProcessRegistry<String, OwnableRequest<String, String>> decoratedProcess = 
            new AuditingProcessRegistryDecorator<>(process, "AssetOwnership");
        DummyOwnableRequest asset = new DummyOwnableRequest();

        // Redirect System.out to capture auditing log outputs
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));

        try {
            // Register through decorator
            decoratedProcess.register("asset-202", asset).unsafeRunSync();

            // Verify decorator printed intercept audit log
            String output = outputStream.toString();
            assertTrue(output.contains("[AUDIT] [REGISTRY] [INTERCEPT]"));
            assertTrue(output.contains("asset-202"));
            assertTrue(output.contains("AssetOwnership"));

            // Verify underlying registration completed successfully
            assertTrue(decoratedProcess.isRegistered("asset-202").unsafeRunSync());

            // Clear buffer and test unregister audit log
            outputStream.reset();
            decoratedProcess.unregister("asset-202").unsafeRunSync();

            String unregisterOutput = outputStream.toString();
            assertTrue(unregisterOutput.contains("[AUDIT] [REGISTRY] [INTERCEPT] Evicting registration"));
            assertTrue(unregisterOutput.contains("asset-202"));

            // Verify underlying unregistration completed successfully
            assertFalse(decoratedProcess.isRegistered("asset-202").unsafeRunSync());
        } finally {
            System.setOut(originalOut);
        }
    }
}
