package io.effects.recipes.observable;

import io.effects.recipes.observable.models.*;

import io.effects.core.Either;
import io.effects.core.IO;
import io.effects.adapters.InMemoryEventSubscriber;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ObservableRecipeTest {

    // Simple test event
    public static record BusinessEvent(String topic, String message, int priority) {}

    // Behavioral subscriber implementation (no getters/setters!)
    private static class BehavioralSubscriber implements ObservableRequest<String, BusinessEvent> {
        private final String allowedTopic;
        private final int minPriority;
        private final List<BusinessEvent> deliveredEvents = Collections.synchronizedList(new ArrayList<>());
        private boolean denyAll = false;

        public BehavioralSubscriber(String allowedTopic, int minPriority) {
            this.allowedTopic = allowedTopic;
            this.minPriority = minPriority;
        }

        public void forceDeny() {
            this.denyAll = true;
        }

        @Override
        public Either<String, Void> checkSubscriptionPermission(String topic) {
            if (denyAll) {
                return Either.left("Subscription explicitly denied");
            }
            if (topic.equals(allowedTopic)) {
                return Either.right(null);
            }
            return Either.left("Unauthorized topic subscription: " + topic);
        }

        @Override
        public boolean shouldDeliver(BusinessEvent event) {
            return event.priority() >= minPriority;
        }

        @Override
        public void onEventDelivered(BusinessEvent event) {
            deliveredEvents.add(event);
        }

        public List<BusinessEvent> getDeliveredEvents() {
            return unmodifiableList(deliveredEvents);
        }

        private static <T> List<T> unmodifiableList(List<T> list) {
            return Collections.unmodifiableList(new ArrayList<>(list));
        }
    }

    @Test
    void testObservableRecipeSubscribeAndEventDelivery() {
        InMemoryEventSubscriber<BusinessEvent> brokerPort = new InMemoryEventSubscriber<>();
        ObservableProcess<String, String, BusinessEvent> process = new ObservableProcess<>(brokerPort);

        BehavioralSubscriber alice = new BehavioralSubscriber("promo-news", 3);
        BehavioralSubscriber bob = new BehavioralSubscriber("promo-news", 5);

        // Register behavioral subscribers
        process.register("alice-id", alice).unsafeRunSync();
        process.register("bob-id", bob).unsafeRunSync();

        Instant now = Instant.now();

        // 1. Subscribe Alice (allowed)
        Either<String, ObservableLedger<String, String>> aliceSub = 
            process.subscribe("system-obs", "alice-id", "promo-news", now).unsafeRunSync();
        assertTrue(aliceSub.isRight());

        // 2. Subscribe Bob (allowed)
        Either<String, ObservableLedger<String, String>> bobSub = 
            process.subscribe("system-obs", "bob-id", "promo-news", now).unsafeRunSync();
        assertTrue(bobSub.isRight());

        // 3. Attempt Unauthorized Subscription (Alice trying to subscribe to "secret-news")
        Either<String, ObservableLedger<String, String>> secretSub = 
            process.subscribe("system-obs", "alice-id", "secret-news", now).unsafeRunSync();
        assertTrue(secretSub.isLeft());
        assertTrue(secretSub.getLeft().contains("Unauthorized topic"));

        // 4. Publish Event 1 (Priority 4)
        BusinessEvent event1 = new BusinessEvent("promo-news", "Discount 10%", 4);
        brokerPort.publish("promo-news", event1).unsafeRunSync();

        // Alice's min priority is 3 -> she should receive event1
        // Bob's min priority is 5 -> he should NOT receive event1
        assertEquals(1, alice.getDeliveredEvents().size());
        assertEquals("Discount 10%", alice.getDeliveredEvents().get(0).message());
        assertEquals(0, bob.getDeliveredEvents().size());

        // 5. Publish Event 2 (Priority 5)
        BusinessEvent event2 = new BusinessEvent("promo-news", "Flash Sale 50%", 5);
        brokerPort.publish("promo-news", event2).unsafeRunSync();

        // Alice should receive event2
        // Bob should receive event2 (since priority 5 >= 5)
        assertEquals(2, alice.getDeliveredEvents().size());
        assertEquals(1, bob.getDeliveredEvents().size());
        assertEquals("Flash Sale 50%", bob.getDeliveredEvents().get(0).message());

        // 6. Unsubscribe Bob
        Either<String, ObservableLedger<String, String>> bobUnsub = 
            process.unsubscribe("system-obs", "bob-id", "promo-news", now).unsafeRunSync();
        assertTrue(bobUnsub.isRight());

        // 7. Publish Event 3 (Priority 6)
        BusinessEvent event3 = new BusinessEvent("promo-news", "BOGO Offer", 6);
        brokerPort.publish("promo-news", event3).unsafeRunSync();

        // Alice receives event3
        // Bob should NOT receive event3 since he is unsubscribed
        assertEquals(3, alice.getDeliveredEvents().size());
        assertEquals(1, bob.getDeliveredEvents().size());
    }

    @Test
    void testPermissionDenialPolicy() {
        InMemoryEventSubscriber<BusinessEvent> brokerPort = new InMemoryEventSubscriber<>();
        ObservableProcess<String, String, BusinessEvent> process = new ObservableProcess<>(brokerPort);

        BehavioralSubscriber charlie = new BehavioralSubscriber("news", 1);
        charlie.forceDeny();

        process.register("charlie-id", charlie).unsafeRunSync();

        Either<String, ObservableLedger<String, String>> subResult = 
            process.subscribe("system-obs", "charlie-id", "news", Instant.now()).unsafeRunSync();
        
        assertTrue(subResult.isLeft());
        assertEquals("Subscription explicitly denied", subResult.getLeft());
    }
}
