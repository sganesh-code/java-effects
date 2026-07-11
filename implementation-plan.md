# Implementation Plan: Event Publish/Subscribe Port, Redis/Kafka Adapters, and Event-Driven Consumer Flows
**Target Repository:** expressj
**Reference Design:** [oo_object_recipe_research_notes.md](expressj/docs/oo_object_recipe_research_notes.md)

This implementation plan details the addition of a high-performance, non-blocking Event Publish/Subscribe Port, enterprise-grade Redis/Kafka Adapters, and a formal business-level "Observable" Recipe. It also outlines the choreography of the e-commerce checkout journey to demonstrate real-world event-driven consumer flows.

---

- [x] **🎟️ [TICKET-001]: Design the EventSubscriber Port & Subscription Abstractions**
  - **Description:** Establish the foundational interfaces for reactive message consumption within the functional `IO` framework. This extends the existing `EventPublisher<E>` port and provides an in-memory subscription engine for testing and validation.
  - **Scope:**
    - **In scope:**
      - Creation of the `EventSubscriber<E>` port interface under `@expressj/core/src/main/java/io/effects/ports/`.
      - Creation of the `Subscription` interface representing an active registration that can be cancelled.
      - Creation of `InMemoryEventSubscriber<E>` to facilitate testing and local debugging.
      - Thread-safe, non-blocking callback management and event dispatching.
    - **Out of scope:**
      - Persistent message broker implementations (Redis or Kafka), which are handled in subsequent tickets.
  - **Implementation Tasks:**
    - [x] Inspect the existing event publisher port at `@expressj/core/src/main/java/io/effects/ports/EventPublisher.java` to align design patterns.
      - *Inspected EventPublisher interface; aligned design to return IO computations and maintain type safety.*
    - [x] Create `@expressj/core/src/main/java/io/effects/ports/EventSubscriber.java` defining:
      ```java
      package io.effects.ports;

      import io.effects.IO;
      import java.util.function.Function;

      public interface EventSubscriber<E> {
          IO<Subscription> subscribe(String topic, Function<E, IO<Void>> handler);
      }
      ```
      - *Created EventSubscriber interface with monadic subscribe method returning a Subscription.*
    - [x] Create `@expressj/core/src/main/java/io/effects/ports/Subscription.java` defining the cancel capability:
      ```java
      package io.effects.ports;

      import io.effects.IO;

      public interface Subscription {
          IO<Void> unsubscribe();
      }
      ```
      - *Created Subscription interface with monadic unsubscribe method.*
    - [x] Create `@expressj/core/src/main/java/io/effects/adapters/InMemoryEventSubscriber.java` implementing `EventSubscriber<E>`. It must manage active subscriber callbacks thread-safely using a concurrent registry (e.g., `ConcurrentHashMap<String, List<Function<E, IO<Void>>>>`).
      - *Created InMemoryEventSubscriber implementation managing subscriber callbacks using a thread-safe CopyOnWriteArrayList inside a ConcurrentHashMap.*
    - [x] Write unit and scenario tests under `@expressj/core/src/test/java/io/effects/ports/EventPubSubTest.java` to verify:
      - Subscribing to a topic successfully triggers the monadic callback when an event is published.
      - Unsubscribing successfully removes the handler and halts further callback invocations.
      - Concurrency safety of multi-threaded publishing and subscribing.
      - *Wrote EventPubSubTest verifying multi-subscriber message routing, unsubscribing, and high-concurrency safe delivery.*

---

- [ ] **🎟️ [TICKET-002]: Implement Redis Pub/Sub & Redis Streams Adapters**
  - **Description:** Implement high-performance, non-blocking Redis adapters for event publication and subscription. We will support both lightweight Redis Pub/Sub (for simple notifications) and robust, persistent Redis Streams (with consumer groups) for enterprise reliability.
  - **Scope:**
    - **In scope:**
      - Declaring Redis client dependency (Jedis or Lettuce) in the project build configuration.
      - Creating `RedisEventPublisher` and `RedisEventSubscriber` adapters.
      - Using Java 21 Virtual Threads (`IO.shift()`) to run the polling and subscription listener loops without blocking OS threads.
      - Implementing connection retry, serialization, and resource lifecycle management.
    - **Out of scope:**
      - Kafka adapters (handled in TICKET-003).
  - **Implementation Tasks:**
    - [ ] Declare Redis client dependency in `@expressj/gradle/libs.versions.toml` (e.g., `redis.clients:jedis:5.1.2` or `io.lettuce:lettuce-core:6.3.2.RELEASE`).
    - [ ] Import the dependency inside `@expressj/core/build.gradle.kts`.
    - [ ] Create `@expressj/core/src/main/java/io/effects/adapters/redis/RedisEventPublisher.java` implementing `EventPublisher<E>`. It must serialize events to JSON (using a serializer like `JacksonStateSerializer`) and publish them to a Redis channel or Stream.
    - [ ] Create `@expressj/core/src/main/java/io/effects/adapters/redis/RedisEventSubscriber.java` implementing `EventSubscriber<E>`. It must run a long-running polling/subscription listener loop on Java 21 Virtual Threads, executing incoming messages inside the consumer's monadic `IO` handler.
    - [ ] Write integration/unit tests under `@expressj/core/src/test/java/io/effects/adapters/redis/RedisPubSubTest.java` utilizing a mock Jedis/Lettuce client or Testcontainers to verify end-to-end event propagation.

---

- [ ] **🎟️ [TICKET-003]: Implement Apache Kafka Adapter with Virtual-Thread Polling**
  - **Description:** Implement enterprise-grade Apache Kafka adapters for the publish/subscribe ports. This adapter must support partitioning, consumer groups, and manual offset committing to guarantee **At-Least-Once** processing semantics.
  - **Scope:**
    - **In scope:**
      - Declaring `kafka-clients` dependency in the build configuration.
      - Creating `KafkaEventPublisher` and `KafkaEventSubscriber` adapters.
      - Leveraging Java 21 Virtual Threads to drive the blocking Kafka consumer `poll()` loop.
      - Designing explicit offset commits that occur *only* after downstream monadic `IO` actions complete successfully.
    - **Out of scope:**
      - Advanced Kafka stream processing (e.g. KStreams). This port remains a clean message broker.
  - **Implementation Tasks:**
    - [ ] Declare Kafka clients dependency in `@expressj/gradle/libs.versions.toml` (e.g., `org.apache.kafka:kafka-clients:3.7.1`).
    - [ ] Add the dependency inside `@expressj/core/build.gradle.kts`.
    - [ ] Create `@expressj/core/src/main/java/io/effects/adapters/kafka/KafkaEventPublisher.java` implementing `EventPublisher<E>`. It must publish serialized events to the designated topic inside referentially transparent `IO.delay(...)` blocks.
    - [ ] Create `@expressj/core/src/main/java/io/effects/adapters/kafka/KafkaEventSubscriber.java` implementing `EventSubscriber<E>`. It must poll Kafka on a virtual thread and feed messages into the consumer handler, committing offsets manually to avoid data loss on downstream failures.
    - [ ] Write tests under `@expressj/core/src/test/java/io/effects/adapters/kafka/KafkaPubSubTest.java` verifying publishing, consumer group subscriptions, and failure recovery.

---

- [ ] **🎟️ [TICKET-004]: Design and Implement the Observable Business Recipe**
  - **Description:** Formalize the `Observable` collaboration recipe, as conceptualized in the research notes, under the new package `io.effects.recipes.observable`. This provides business-level grammar for consumers to declare event-driven, reactive subscriptions using filters and notification policies.
  - **Scope:**
    - **In scope:**
      - Designing `ObservableProcess`, `ObservableRequest`, and related roles/events.
      - Supporting declarative `SubscriptionFilter` and `NotificationPolicy` objects.
      - Emitting events: `SubscriptionCreated`, `EventPublished`, and `NotificationSent`.
    - **Out of scope:**
      - Directly altering existing recipe processes; other recipes will interact strictly through the unified publish/subscribe ports.
  - **Implementation Tasks:**
    - [ ] Read `@expressj/docs/oo_object_recipe_research_notes.md` (specifically Sections 9 & 10) to align with the conceptual blueprint for the `Observable` recipe.
    - [ ] Create interface `@expressj/core/src/main/java/io/effects/recipes/observable/ObservableRequest.java` and record/class `@expressj/core/src/main/java/io/effects/recipes/observable/ObservableProcess.java`.
    - [ ] Implement core methods:
      - `subscribe(String topic, SubscriberID subscriberId, SubscriptionFilter filter)`: Declares subscriber intent.
      - `publish(E event)`: Distributes events.
    - [ ] Design replaceable policy objects: `SubscriptionFilter` (evaluates predicates on events) and `NotificationPolicy` (determines error retry/escalation when a subscriber notify fails).
    - [ ] Write comprehensive unit and invariant tests under `@expressj/core/src/test/java/io/effects/recipes/observable/ObservableRecipeTest.java` verifying filtering, multi-subscriber notifications, and concurrency safety.

---

- [ ] **🎟️ [TICKET-005]: Decouple & Choreograph Sample E-Commerce Checkout Journey**
  - **Description:** Transition the e-commerce checkout journey from a sequential, orchestrator-driven application to a decentralized, event-driven choreographed system. The domain objects (such as `LogisticsProvider` and `AssetRegistry`) will react directly to events from the broker ports, enabling true asynchronous choreography within the business context.
  - **Scope:**
    - **In scope:**
      - Updating the sample e-commerce application domain objects to natively use the `EventSubscriber` capability.
      - Decoupling sequential invocation in `EcommerceApp.java` into an event-driven flow.
      - Having `LogisticsProvider` automatically trigger item allocation when it receives a `HoldConfirmed` event.
      - Having `AssetRegistry` automatically grant warranty and SLA entitlements upon receiving `FulfillmentCompleted` events.
      - Validating system flows under asynchronous concurrency.
    - **Out of scope:**
      - Changing core business rules or mathematical invariants of the aggregate ledgers (which remain strictly complete and encapsulated).
  - **Implementation Tasks:**
    - [ ] Analyze the existing domain objects under `@expressj/samples/ecommerce-checkout-journey/src/main/java/io/effects/samples/ecommerce/domain/`.
    - [ ] Refactor `LogisticsProvider` to subscribe to `HoldConfirmed` events using the `EventSubscriber` port on system startup.
    - [ ] Refactor `AssetRegistry` to subscribe to `FulfillmentCompleted` events using the `EventSubscriber` port.
    - [ ] Refactor `EcommerceApp.java` (specifically the simulation runner loop) to wire these subscribers on startup, then initiate the checkout flow and let messages propagate asynchronously via Redis/Kafka.
    - [ ] Write integration tests under `@expressj/samples/ecommerce-checkout-journey/src/test/java/io/effects/samples/ecommerce/EcommerceAppEventDrivenTest.java` to verify the complete, asynchronous propagation of the checkout journey.
    - [ ] Execute `./gradlew clean test` to ensure all existing and new tests pass without regressions.
