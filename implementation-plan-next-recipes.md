# Implementation Plan: Reusable Catalog Recipes (Payable, Fulfillable, Ownable, Schedulable)
**Target Repository:** java-effects
**Reference Design:** `@java-effects/docs/oo_object_recipe_research_notes.md`

The following tickets break down the implementation of the next set of reusable business-level object collaboration recipes from the candidate catalog. They will be implemented inside the core `:lib` project. 

All of these recipes will strictly leverage our root-level, generalized ports and adapters (`io.effects.ports.EventPublisher`, `StateRepository`, `TelemetryPort` and their respective generic `io.effects.adapters` implementations).

---

- [x] **🎟️ [RECIPE-003]: Implement the Ownable Object Collaboration Recipe**
  - **Description:** 
    Implement the `Ownable` recipe, representing a reusable, domain-agnostic protocol for managing asset or entity ownership (assignment, transfer, validation, and revocation).
    
    **Underlying Laws & Invariants of the Algebra:**
    1. *Exclusivity:* At any given point in time, an Ownable asset can have at most one active owner.
    2. *Validation before Action:* Any ownership-sensitive transition must verify that the requesting actor matches the current owner.
    3. *Auditability:* Every ownership assignment, transfer, or revocation must append a step to an immutable audit trail.
    4. *Revocation Finality:* A revoked owner cannot execute transfers or assign new owners without a fresh, valid owner assignment.
    
  - **Scope:**
    - **In scope:**
      - Interfaces, enums, records, and processes in package `io.effects.recipes.ownable`:
        - `OwnableRequest`: Pure behavioral synchronous domain interface representing an asset to be owned.
        - `OwnershipRecord`: Reusable, thread-safe domain state ledger tracking current owner and history.
        - `OwnershipStep`: Immutable entry in the ownership audit trail.
        - `OwnableProcess`: Monadic process manager (infrastructure engine) executing in `IO` to orchestrate assignments, transfers, and revocations.
      - Event classes in `io.effects.recipes.ownable`:
        - `OwnershipAssigned`, `OwnershipTransferred`, `OwnershipRevoked` (implementing `io.effects.ports.EventPublisher` generic event interface).
      - Utilizing root-level generic ports:
        - `StateRepository<String, OwnershipRecord>` for persistence.
        - `EventPublisher<OwnershipEvent>` for external notifications.
        - `TelemetryPort` for operational metrics.
    - **Out of scope:**
      - Multi-factor authentication mechanisms or external directory lookups.

  - **Implementation Tasks:**
    - [x] **Design Contracts:**
      - *Created OwnableRequest pure behavioral interface, OwnershipRecord thread-safe domain ledger, and OwnershipStep immutable step record.*
      - *Created OwnershipEvent base interface and OwnershipAssigned, OwnershipTransferred, and OwnershipRevoked concrete events.*
      - Define `OwnershipStep`, `OwnershipRecord`, and the `OwnableRequest` interface under `@java-effects/lib/src/main/java/io/effects/recipes/ownable`.
    - [x] **Implement Process Engine:**
      - *Created OwnableProcess.java coordinating repository actions, generic events publication, and operational telemetry recording inside lazy IO monads.*
      - Create `OwnableProcess.java` with monadic pipelines for `assignOwner`, `transferOwner`, and `revokeOwner`.
    - [x] **Unit Tests:**
      - *Created OwnableRecipeTest.java under java-effects test folder. Implemented robust scenario testing for initial assignment, Bob transfers, admin revocations, immutable audit checks, event emissions, and custom telemetry.*
      - Create `OwnableRecipeTest.java` in `src/test/java` to test ownership validation, audit trails, and revocation blocks.

---

- [x] **🎟️ [RECIPE-004]: Implement the Payable Object Collaboration Recipe**
  - **Description:** 
    Implement the `Payable` (Settleable) object collaboration recipe, representing a reusable protocol for payment flows (authorizations, captures, reversals, and refunds).
    
    **Underlying Laws & Invariants of the Algebra:**
    1. *Authorization Bound:* The captured amount must never exceed the authorized amount.
    2. *Refund Bound:* The cumulative refunded amount must never exceed the captured amount.
    3. *State Finality:* A failed authorization cannot be captured; a reversed/expired authorization is terminal.
    4. *Double-Capture Block:* A duplicate capture command with the same transaction key returns idempotent success.
    
  - **Scope:**
    - **In scope:**
      - Interfaces and records in package `io.effects.recipes.payable`:
        - `PayableRequest`: Pure behavioral synchronous domain interface representing a payment instruction.
        - `PaymentLedger`: Reusable thread-safe domain state ledger tracking payment lifecycle, authorizations, captures, and refunds.
        - `PaymentStep`: Immutable entry in the financial audit trail.
        - `PaymentStatus` and `StepType` enums.
        - `PayableProcess`: Monadic process manager coordinating repository loading, domain evaluations, event publishing, and telemetry in `IO`.
      - Event classes in `io.effects.recipes.payable`:
        - `PaymentAuthorized`, `PaymentCaptured`, `PaymentRefunded`, `PaymentReversed`.
    - **Out of scope:**
      - Direct PCI-compliant card handling or third-party bank connections.

  - **Implementation Tasks:**
    - [x] **Design Contracts:**
      - *Created PayableRequest pure behavioral, getter-free interface, PaymentLedger non-anemic domain state ledger, and PaymentStep immutable audit step record.*
      - *Created PaymentEvent base interface and PaymentAuthorized, PaymentCaptured, PaymentReversed, and PaymentRefunded concrete event implementations.*
      - Define enums, `PaymentStep`, `PaymentLedger`, and the `PayableRequest` interface under `@java-effects/lib/src/main/java/io/effects/recipes/payable`.
    - [x] **Implement Process Engine:**
      - *Created PayableProcess.java coordinating repository actions, generic events publication, and operational telemetry recording inside lazy IO monads.*
      - Create `PayableProcess.java` implementing the monadic pipelines for `authorize`, `capture`, `reverse`, and `refund` methods using `ForIO`.
    - [x] **Unit Tests:**
      - *Created PayableRecipeTest.java under java-effects test folder. Implemented robust scenario testing for initial authorization bounds, multi-step captures, authorization reversals, and refund cap constraints.*
      - Create `PayableRecipeTest.java` in `src/test/java` to test authorization caps, multi-capture idempotency, and refund limits.

---

- [x] **🎟️ [RECIPE-005]: Implement the Fulfillable Object Collaboration Recipe**
  - **Description:** 
    Implement the `Fulfillable` object collaboration recipe, modeling the lifecycle of product or service fulfillment (allocation, packaging, dispatch, and delivery).
    
    **Underlying Laws & Invariants of the Algebra:**
    1. *Sequential Progression:* Items must be allocated before they can be packaged; packaged before dispatched; and dispatched before completed.
    2. *Release Capability:* Allocated or packaged items can be released back to the source pool at any time before dispatch.
    3. *Dispatch Terminal Transition:* Once dispatched, the fulfillment process is active in-transit and cannot be revoked/released.
    4. *Partial Fulfillment Support:* The ledger must cleanly track partial item allocations and partial dispatches.
    
  - **Scope:**
    - **In scope:**
      - Interfaces and records in package `io.effects.recipes.fulfillable`:
        - `FulfillableRequest`: Pure behavioral synchronous domain interface representing a fulfillment order.
        - `FulfillmentLedger`: Reusable thread-safe domain ledger tracking item status (allocated, packaged, in-transit, delivered).
        - `FulfillmentStep`: Immutable audit trail step.
        - `FulfillmentStatus` and `StepType` enums.
        - `FulfillmentProcess`: Monadic orchestrator managing the state transitions in `IO`.
      - Event classes in `io.effects.recipes.fulfillable`:
        - `FulfillmentAllocated`, `FulfillmentDispatched`, `FulfillmentCompleted`, `FulfillmentReleased`.
    - **Out of scope:**
      - Integration with specific shipping carrier APIs (FedEx, UPS).

  - **Implementation Tasks:**
    - [x] **Design Contracts:**
      - *Created FulfillableRequest pure behavioral interface, FulfillmentLedger thread-safe state container, and FulfillmentStep immutable step.*
      - *Created FulfillmentEvent base interface and FulfillmentAllocated, FulfillmentDispatched, FulfillmentCompleted, and FulfillmentReleased concrete events.*
      - Define enums, ledgers, and `FulfillableRequest` interface under `@java-effects/lib/src/main/java/io/effects/recipes/fulfillable`.
    - [x] **Implement Process Engine:**
      - *Created FulfillmentProcess.java coordinating repository actions, generic events publication, and operational telemetry recording inside lazy IO monads.*
      - Create `FulfillmentProcess.java` with monadic pipelines for `allocate`, `package`, `dispatch`, `complete`, and `release`.
    - [x] **Unit Tests:**
      - *Created FulfillmentRecipeTest.java under java-effects test folder. Implemented robust scenario testing for sequential progression, partial allocations, release limits, and transit terminal state blocks.*
      - Create `FulfillmentRecipeTest.java` in `src/test/java` verifying item status sequence progression, partial fulfillment math, and release invariants.

---

- [x] **🎟️ [RECIPE-006]: Implement the Schedulable Object Collaboration Recipe**
  - **Description:** 
    Implement the `Schedulable` object collaboration recipe, representing a reusable, domain-agnostic protocol for scheduling, adjusting, executing, and cancelling timed occurrences (SLA deadlines, reminder alerts, or recurring runs).
    
    **Underlying Laws & Invariants of the Algebra:**
    1. *Temporal Progress:* An event can only be fired after its scheduled trigger time has passed on the provided Clock.
    2. *Immutable Expiry:* Once fired or cancelled, a scheduled event is in a terminal state and cannot be rescheduled or fired again.
    3. *Adjustable In-Flight:* Events in the active `SCHEDULED` state can be freely adjusted or rescheduled to a different trigger time.
    4. *Explainability:* Rescheduling or cancellations must record matching reasons in the schedule history.
    
  - **Scope:**
    - **In scope:**
      - Interfaces, records, and processes in package `io.effects.recipes.schedulable`:
        - `SchedulableRequest`: Pure behavioral synchronous domain interface representing a timed task or event.
        - `ScheduleLedger`: Reusable thread-safe domain ledger tracking the schedule status, execution time, and adjustment history.
        - `ScheduleStep`: Immutable entry in the schedule audit trail.
        - `ScheduleStatus` and `StepType` enums.
        - `SchedulableProcess`: Monadic process manager coordinating scheduler loading, domain clock checks, execution, and cancellation in `IO`.
      - Event classes in `io.effects.recipes.schedulable`:
        - `OccurrenceScheduled`, `OccurrenceRescheduled`, `OccurrenceFired`, `OccurrenceCancelled`.
    - **Out of scope:**
      - Direct OS-level cron thread pools or persistent system timers.

  - **Implementation Tasks:**
    - [x] **Design Contracts:**
      - *Created SchedulableRequest pure behavioral interface, ScheduleLedger thread-safe state container, and ScheduleStep immutable step.*
      - *Created SchedulableEvent base interface and OccurrenceScheduled, OccurrenceRescheduled, OccurrenceFired, and OccurrenceCancelled concrete events.*
      - Define `ScheduleStep`, `ScheduleLedger`, and the `SchedulableRequest` interface under `@java-effects/lib/src/main/java/io/effects/recipes/schedulable`.
    - [x] **Implement Process Engine:**
      - *Created SchedulableProcess.java coordinating repository actions, generic events publication, and operational telemetry recording inside lazy IO monads.*
      - Create `SchedulableProcess.java` with monadic pipelines for `schedule`, `reschedule`, `cancel`, and `fire`.
    - [x] **Unit Tests:**
      - *Created SchedulableRecipeTest.java under java-effects test folder. Implemented robust scenario testing for initial scheduling future-trigger bounds, in-flight trigger time reschedules, temporal progress execution checks, and immutable cancelled terminal state blocks.*
      - Create `SchedulableRecipeTest.java` in `src/test/java` testing rescheduling capabilities, clock trigger checks, and cancellation invariants.

---

- [x] **🎟️ [RECIPE-VERIFY]: Epic Verification, Testing, and Documentation**
  - **Description:** 
    Perform project-wide build verification, test suite execution, and documentation updates for the recipe catalog.
    
  - **Scope:**
    - **In scope:**
      - Executing `./gradlew test` to ensure 100% test coverage across all built recipes.
      - Updating `@java-effects/docs/oo_object_recipe_research_notes.md` to reference the newly implemented recipes.
    - **Out of scope:**
      - Modifying any stable core monadic modules.

  - **Implementation Tasks:**
    - [x] **Gradle Build Verification:**
      - *Executed full Gradle multi-task build check (./gradlew build), confirming 100% build success and test verification across all catalog modules.*
      - Execute `./gradlew clean build check test` to verify complete compile and test runs.
    - [x] **Documentation Update:**
      - *Successfully updated oo_object_recipe_research_notes.md, documenting completed catalog recipes, generic ports/adapters abstractions, and the rich aggregate design pattern.*
      - Record architectural design choices and user guide details for the new recipes.
