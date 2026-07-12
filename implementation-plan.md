# Implementation Plan: Advanced Core OO Collaboration Recipes (Iteration 2)
**Target Repository:** expressj
**Reference Design:** [oo_object_recipe_research_notes.md](expressj/docs/oo_object_recipe_research_notes.md)

The following tickets break down the implementation of the next set of advanced reusable object collaboration recipes identified for the `expressj` library, adhering strictly to pure OOP principles (zero getters, tell-dont-ask, state projection, and behavioral collaboration).

---

- [x] **🎟️ [TICKET-001]: Implement Prioritizable Collaboration Recipe**
  - **Description:** Implement a generic `Prioritizable` collaboration recipe that structures the triage, sequencing, deferral, and expedition of work items. The design must adhere strictly to pure OOP principles: no simple getter methods, no exposure of internal state lists, and using a visitor-based state projection or behavioral queries.
  - **Scope:**
    - **In scope:**
      - Non-anemic `PriorityLedger<ID, P, C>` aggregate root tracking priority states (`UNTRIAGED`, `SEQUENCED`, `DEFERRED`, `EXPEDITED`) without exposing simple getter methods.
      - A state projection pattern (e.g. `PriorityProjector<ID, P, C>`) to allow safe, controlled inspection of ledger history and values for test verification.
      - Getter-free, behavioral `PrioritizableRequest<ID, P, C>` interface for validating priority assignments, deferrals, and escalations.
      - Monadic `PrioritizableProcess<ID, P, C>` process manager extending `Recipe` and utilizing `ProcessCoordinator` inside `IO`.
      - Domain events: `WorkSequenced`, `WorkReprioritized`, `WorkDeferred`, `WorkExpedited` under `io.effects.recipes.prioritizable.models`.
      - Comprehensive unit and integration tests under `expressj/core/src/test/java/io/effects/recipes/prioritizable/` checking all invariants, sequence laws, and backpressure actions.
    - **Out of scope:**
      - Hardcoded priority values, sorting comparator libraries, or direct database query builders.
  - **Implementation Tasks:**
    - [x] Create package `io.effects.recipes.prioritizable` and sub-package `models`.
      - *Created package directories and files under `io.effects.recipes.prioritizable` and `models`.*
    - [x] Implement `PrioritizableEvent<ID, P>` and concrete event models:
      - `WorkSequenced<ID, P>`
      - `WorkReprioritized<ID, P>`
      - `WorkDeferred<ID, P>`
      - `WorkExpedited<ID, P>`
      - *Implemented the generic `PrioritizableEvent` interface, `PriorityStep` record, and concrete event record models `WorkSequenced`, `WorkReprioritized`, `WorkDeferred`, and `WorkExpedited`.*
    - [x] Define the getter-free interface `PrioritizableRequest<ID, P, C>` specifying behavioral callbacks:
      - `evaluateInitialPriority(...)`
      - `evaluateReprioritization(...)`
      - `evaluateDeferral(...)`
      - `evaluateExpedition(...)`
      - *Created the getter-free behavioral interface defining pure synchronous double-dispatch validation rules.*
    - [x] Implement the `PriorityLedger<ID, P, C>` aggregate root. Ensure it has **zero simple getters**, is thread-safe (`synchronized`), and implements a `projectState(PriorityProjector<ID, P, C>)` visitor.
      - *Implemented the completely getter-free thread-safe `PriorityLedger` aggregate root with visitor-based state projection and behavioral query helpers.*
    - [x] Create `PrioritizableProcess<ID, P, C>` extending `Recipe` that coordinates persistence, event publishing, and telemetry using `ProcessCoordinator`.
      - *Created the monadic `PrioritizableProcess` coordinating state persistence, event publication, and telemetry within functional `IO` shell.*
    - [x] Add unit and integration tests under `expressj/core/src/test/java/io/effects/recipes/prioritizable/` verifying:
      - Successful sequencing and reprioritization paths.
      - Deferral validation and timed checks.
      - Expedition paths.
      - Pure OOP law validation (state is protected, getters are absent, verification is done through events and projectors).
      - *Created exhaustive test suite in `PrioritizableRecipeTest` verifying all priority transitions, validating deferrals and expeditions, and demonstrating pure OOP verification using the `PriorityProjector` visitor pattern.*

---

- [ ] **🎟️ [TICKET-002]: Implement Compensable Collaboration Recipe**
  - **Description:** Implement a generic `Compensable` collaboration recipe to coordinate semantic "undo" or reversal actions for multi-step distributed operations (Saga pattern) without exposing internal execution state.
  - **Scope:**
    - **In scope:**
      - Non-anemic `CompensationLedger<ID, C>` tracking Saga transaction statuses (`INITIAL`, `STEP_COMPLETED`, `COMPENSATING`, `COMPENSATED`, `FAILED`) without simple getters.
      - Getter-free, behavioral `CompensableRequest<ID, C>` representing the Saga execution logic and validation criteria.
      - Monadic `CompensableProcess<ID, C>` coordinating step execution, catching downstream failures, and running compensating operations in `IO`.
      - Domain events: `SagaStepSucceeded`, `SagaRollbackTriggered`, `SagaCompensated`, `SagaCompensatedFailed`.
    - **Out of scope:**
      - Distributed locking mechanics or persistent network database transactions.
  - **Implementation Tasks:**
    - [ ] Create package `io.effects.recipes.compensable` and sub-package `models`.
    - [ ] Define compensable event types under `models`.
    - [ ] Define behavioral request interface `CompensableRequest<ID, C>`.
    - [ ] Build rich aggregate root `CompensationLedger<ID, C>` with a state projection interface.
    - [ ] Create `CompensableProcess<ID, C>` extending `Recipe` to orchestrate step execution and rollback in `IO`.
    - [ ] Add comprehensive tests verifying Saga completion, automatic compensation triggering on failures, and final terminal audits.

---

- [ ] **🎟️ [TICKET-003]: Implement Escalatable Collaboration Recipe**
  - **Description:** Implement a generic `Escalatable` collaboration recipe to handle time-sensitive re-assignments, SLA breaches, and authority promotion for cases or transactions.
  - **Scope:**
    - **In scope:**
      - Non-anemic `EscalationLedger<ID, T, C>` tracking states (`STANDARD`, `SLA_WARNING`, `ESCALATED`, `REASSIGNED`, `RESOLVED`) without exposing simple state getters.
      - Getter-free, behavioral `EscalatableRequest<ID, T, C>` defining SLA and promotion validations.
      - Monadic `EscalatableProcess<ID, T, C>` coordinator running under monadic `IO`.
      - Domain events: `SLAWarningTriggered`, `CaseEscalated`, `CaseDeescalated`, `CaseReassigned`.
    - **Out of scope:**
      - Direct email notifications or SMS dispatch integrations.
  - **Implementation Tasks:**
    - [ ] Create package `io.effects.recipes.escalatable` and sub-package `models`.
    - [ ] Define escalation event types and step records.
    - [ ] Define behavioral request interface `EscalatableRequest<ID, T, C>`.
    - [ ] Build rich aggregate root `EscalationLedger<ID, T, C>` with visitor state projection.
    - [ ] Create `EscalatableProcess<ID, T, C>` extending `Recipe` using `ProcessCoordinator`.
    - [ ] Add unit tests verifying SLA breaches, authority escalation, re-assignment, and de-escalation lifecycles.

---

- [ ] **🎟️ [TICKET-004]: Implement Throttlable Collaboration Recipe**
  - **Description:** Implement a generic `Throttlable` collaboration recipe that manages rate-limiting, token consumption, sliding window histories, and backpressure enforcement.
  - **Scope:**
    - **In scope:**
      - Non-anemic `TokenBucketLedger<ID, C>` tracking token capacities (`ALLOWED`, `THROTTLED`, `RESTORED`) with **zero getters**.
      - Getter-free, behavioral `ThrottlableRequest<ID, C>` interface defining dynamic capacity and refill rates.
      - Monadic `ThrottlableProcess<ID, C>` coordinating adaptive consumption and quota refills inside `IO`.
      - Domain events: `TokensConsumed`, `RateThrottled`, `QuotaRefilled`.
    - **Out of scope:**
      - Distributed Redis-backed rate limiters (only local-to-node thread-safe JVM memory limiters are managed).
  - **Implementation Tasks:**
    - [ ] Create package `io.effects.recipes.throttlable` and sub-package `models`.
    - [ ] Define throttlable event types and state buckets.
    - [ ] Define behavioral request interface `ThrottlableRequest<ID, C>`.
    - [ ] Build rich aggregate root `TokenBucketLedger<ID, C>` with visitor projection.
    - [ ] Create `ThrottlableProcess<ID, C>` extending `Recipe`.
    - [ ] Add unit tests verifying token consumption, sliding backpressure delays, automatic refills, and quota limits.
