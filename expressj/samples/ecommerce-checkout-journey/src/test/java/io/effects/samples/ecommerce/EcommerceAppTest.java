package io.effects.samples.ecommerce;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EcommerceAppTest {

    @Test
    void testEndToEndBulkCheckoutJourney() {
        // Assert that running our happy path simulation executes without throwing any exceptions
        assertDoesNotThrow(EcommerceApp::runHappyPathSimulation);
    }
}
