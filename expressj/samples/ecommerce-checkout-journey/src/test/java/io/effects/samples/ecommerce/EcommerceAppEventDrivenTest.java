package io.effects.samples.ecommerce;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EcommerceAppEventDrivenTest {

    @Test
    void testChoreographedECommerceCheckoutJourney() {
        // Verify that the event-driven orchestrated/choreographed simulation run successfully without throwing exceptions
        assertDoesNotThrow(EcommerceApp::runHappyPathSimulation);
    }
}
