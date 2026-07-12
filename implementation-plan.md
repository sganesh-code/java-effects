# Implementation Plan: New Core OO Collaboration Recipes
**Target Repository:** expressj
**Reference Design:** [oo_object_recipe_research_notes.md](expressj/docs/oo_object_recipe_research_notes.md)

The following tickets break down the implementation of the next set of reusable object collaboration recipes identified for the `expressj` library.

---

- [x] **🎟️ [TICKET-001]: Implement Routable Collaboration Recipe**
  - **Description:** Implement a generic `Routable` collaboration recipe that directs work units or tasks to appropriate destinations or handlers. It coordinates dynamic handler evaluation based on policy-driven rules, ownership/capacity constraints, or load balancing.
  - **Scope:**
    - **In scope:**
      - Non-anemic `RouteLedger<ID, H, C>` aggregate root tracking route states (`UNROUTED`, `ROUTING`, `ROUTED`, `REJECTED`, `REROUTING`) and historical step decisions.
      - Getter-free, behavioral `RoutableRequest<ID, H, C>` interface for synchronous routing decision callbacks (double dispatch).
      - Monadic `RoutableProcess<ID, H, C>` process manager extending `Recipe` and leveraging `ProcessCoordinator` to execute operations inside `IO`.
      - Domain events: `WorkRouted`, `WorkRerouted`, `RoutingRejected` under `io.effects.recipes.routable.models`.
      - In-memory state repository, event publisher, and logging telemetry validation.
    - **Out of scope:**
      - Distributed clustering algorithms, network messaging brokers, or direct thread-pool executors.
  - **Implementation Tasks:**
    - [x] Create package `io.effects.recipes.routable` and sub-package `models`.
      - *Created the package directories and files under `io.effects.recipes.routable` and `models`.*
    - [x] Implement `RoutableEvent<ID, H>` and concrete event models:
      - `WorkRouted<ID, H>`
      - `WorkRerouted<ID, H>`
      - `RoutingRejected<ID>`
      - *Implemented the generic `RoutableEvent` interface, `RoutingStep` record, and concrete record events: `WorkRouted`, `WorkRerouted`, and `RoutingRejected`.*
    - [x] Define the getter-free interface `RoutableRequest<ID, H, C>` specifying behavioral callbacks:
      - `evaluateInitialRoute(...)`
      - `evaluateReroute(...)`
      - `evaluateRejection(...)`
      - *Created the getter-free, behavioral `RoutableRequest` interface supporting double dispatch validation.*
    - [x] Implement the `RouteLedger<ID, H, C>` aggregate root. Ensure it is thread-safe (`synchronized`), keeps an immutable chronological history of routing decisions, and encapsulates state transition invariants.
      - *Implemented the thread-safe `RouteLedger` aggregate root with full state machine tracking, transition validations, and chronological steps.*
    - [x] Create `RoutableProcess<ID, H, C>` extending `Recipe<ID, RoutableRequest<ID, H, C>>` that coordinates persistence, event publishing, and telemetry using the `ProcessCoordinator`.
      - *Created the monadic `RoutableProcess` coordinating persistence, event publisher, and telemetry logging inside standard functional `IO` shell.*
    - [x] Add unit and integration tests under `expressj/core/src/test/java/io/effects/recipes/routable/` verifying:
      - Successful direct routing paths.
      - Escalated/rerouted scenarios.
      - Routing rejection handling and explanation.
      - Law verification (e.g. cannot reroute a rejected route without reopening, historical preservation).
      - *Created complete suite of tests in `RoutableRecipeTest` covering all routing transition lifecycles, validation rules, idempotency, and law verification.*

---

- [ ] **🎟️ [TICKET-002]: Implement Reconciliable Collaboration Recipe**
  - **Description:** Implement a generic `Reconciliable` collaboration recipe designed to compare and match two state records (internal ledger vs. external source), tracking matching states, identifying discrepancies, and supporting resolution actions.
  - **Scope:**
    - **In scope:**
      - Non-anemic `ReconciliationLedger<ID, K, E, C>` tracking statuses (`UNMATCHED`, `MATCHED`, `DISCREPANCY`, `RESOLVED`) and mismatch history.
      - Getter-free, behavioral `ReconciliableRequest<ID, K, E, C>` interface to define match rules and discrepancy handling callbacks.
      - Monadic `ReconciliableProcess<ID, K, E, C>` coordinating verification, saving state, publishing events, and logging telemetry.
      - Domain events: `ItemMatched`, `DiscrepancyFlagged`, `DiscrepancyResolved`.
    - **Out of scope:**
      - Brittle CSV parsing, physical database transaction synchronization, or direct TCP connection polling.
  - **Implementation Tasks:**
    - [ ] Create package `io.effects.recipes.reconciliable` and sub-package `models`.
    - [ ] Define reconciliation event types: `ReconciliationEvent<ID, K>`, `ItemMatched<ID, K>`, `DiscrepancyFlagged<ID, K>`, `DiscrepancyResolved<ID, K>`.
    - [ ] Define behavioral request interface `ReconciliableRequest<ID, K, E, C>` exposing:
      - `evaluateMatching(...)`
      - `evaluateDiscrepancy(...)`
      - `evaluateResolution(...)`
    - [ ] Build the rich aggregate root `ReconciliationLedger<ID, K, E, C>` capturing item matching, mismatch codes, resolution audit records, and state transitions.
    - [ ] Create `ReconciliableProcess<ID, K, E, C>` process coordinator using `ProcessCoordinator` to orchestrate reconciliation executions in `IO`.
    - [ ] Add comprehensive tests under `expressj/core/src/test/java/io/effects/recipes/reconciliable/` verifying:
      - Perfect matching flow (reaches `MATCHED` terminal state).
      - Multi-stage mismatch/discrepancy flagging and resolution audit preservation.
      - Double match prevention and status laws.

---

- [ ] **🎟️ [TICKET-003]: Implement Retryable Collaboration Recipe**
  - **Description:** Implement a generic `Retryable` collaboration recipe that manages the execution of transiently-failing actions, orchestrating attempts according to modular backoff policies, recording success/failure histories, and enforcing terminal abandonment/fallback when thresholds are exceeded.
  - **Scope:**
    - **In scope:**
      - Non-anemic `RetryLedger<ID, C>` tracking states (`PENDING`, `ATTEMPTING`, `SUCCEEDED`, `RETRY_PENDING`, `FAILED`) and step histories of exception details and backoff calculations.
      - Getter-free, behavioral `RetryableRequest<ID, C>` interface for assessing if a failure is transient and calculating next backoff delay.
      - Monadic `RetryableProcess<ID, C>` which safely schedules retry delayed tasks using functional monadic `IO` timers.
      - Domain events: `ExecutionSucceeded`, `RetryScheduled`, `AttemptFailed`, `ExecutionAbandoned`.
    - **Out of scope:**
      - Deep system-level `Thread.sleep()` threads or direct OS-level job schedulers. Delay execution must leverage monadic `IO.delay` or asynchronous task executors.
  - **Implementation Tasks:**
    - [ ] Create package `io.effects.recipes.retryable` and sub-package `models`.
    - [ ] Define retry event types: `RetryableEvent<ID>`, `ExecutionSucceeded<ID>`, `RetryScheduled<ID>`, `AttemptFailed<ID>`, `ExecutionAbandoned<ID>`.
    - [ ] Define behavioral request interface `RetryableRequest<ID, C>` for assessing error categorization and defining backoff policy (e.g. constant, exponential with jitter).
    - [ ] Build the non-anemic `RetryLedger<ID, C>` aggregate root to capture attempts count, error messages, and state progression.
    - [ ] Implement `RetryableProcess<ID, C>` extending `Recipe` to execute the runnable operations, log telemetry, publish retry events, and manage scheduler delays.
    - [ ] Add rigorous unit tests under `expressj/core/src/test/java/io/effects/recipes/retryable/` verifying:
      - Instant success path (no retries).
      - Brittle operation that fails once, retries with backoff delay, and then succeeds.
      - Permanent failure path that exceeds retry limits and transitions to terminal `FAILED` (emits `ExecutionAbandoned`).
      - Invariants validation (e.g. attempt counters, maximum retry limitations, thread-safety).

---

- [ ] **🎟️ [TICKET-004]: Implement Claimable Collaboration Recipe**
  - **Description:** Implement a generic `Claimable` collaboration recipe that structures the process of asserting a dispute, request, or factual claim, managing multi-step review, verification, and final acceptance/denial actions.
  - **Scope:**
    - **In scope:**
      - Non-anemic `ClaimLedger<ID, V, C>` tracking states (`FILED`, `UNDER_REVIEW`, `DISPUTED`, `ACCEPTED`, `DENIED`) and historical evidence evaluations.
      - Getter-free, behavioral `ClaimableRequest<ID, V, C>` interface representing double dispatch validation for reviews.
      - Monadic `ClaimableProcess<ID, V, C>` orchestrator executing transitions under `IO`.
      - Domain events: `ClaimFiled`, `ClaimUnderReview`, `ClaimAccepted`, `ClaimDenied`, `ClaimDisputed`.
    - **Out of scope:**
      - Direct storage of raw large files/PDFs (only metadata links are managed), physical document verification, or electronic signature integrations.
  - **Implementation Tasks:**
    - [ ] Create package `io.effects.recipes.claimable` and sub-package `models`.
    - [ ] Define claim event types: `ClaimableEvent<ID>`, `ClaimFiled<ID>`, `ClaimUnderReview<ID>`, `ClaimAccepted<ID>`, `ClaimDenied<ID>`, `ClaimDisputed<ID>`.
    - [ ] Define behavioral request interface `ClaimableRequest<ID, V, C>` for assessing submitted evidence.
    - [ ] Implement `ClaimLedger<ID, V, C>` aggregate root representing reviews chronology, verification state, and decision logs.
    - [ ] Build `ClaimableProcess<ID, V, C>` extending `Recipe` and leveraging `ProcessCoordinator`.
    - [ ] Add unit tests under `expressj/core/src/test/java/io/effects/recipes/claimable/` verifying:
      - Direct accept/deny paths.
      - Dispute and re-evaluation flows.
      - Evidence-based validator constraints.
