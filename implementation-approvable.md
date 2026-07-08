# Implementation Plan: APPROVABLE - The Approvable Object Collaboration Recipe

- [x] **🎟️ [APPROVABLE-001]: Implement the Approvable Object Collaboration Recipe**
  - **Description:** 
    Following the exact methodology, principles, and architectural standards of the `reservable` recipe, implement the `Approvable` object collaboration recipe.
    
    This recipe defines a reusable message-based collaboration protocol for requesting permission/authorization before performing an action (e.g. expense report approvals, medical procedure approvals, access grant approvals). It is grounded in Alan Kay's OOP vision (messaging, local retention/hiding of state-process, and extreme late binding) and functions as an OO equivalent of a functional typeclass by enforcing explicit rules, state transitions, and laws.
    
    **Underlying Laws & Invariants of the Algebra:**
    1. *No Action after Terminal States:* Once a request has reached a terminal state (`APPROVED` or `REJECTED`), no further approvals, rejections, or escalations can be processed (re-evaluating is blocked or returns idempotent success).
    2. *No Approval after Rejection:* A rejected request cannot be approved or escalated without starting a new request or explicit reopening.
    3. *Authority Validation:* A decision (approval/escalation) is valid only if the actor's role meets the policy requirements at decision time.
    4. *Escalation preserves audit history:* Every transition is recorded as an immutable decision step (`ApprovalDecision`) in the chronology, ensuring auditability.
    5. *Decisions must be explainable:* Every transition requires an associated comment or explanation, preventing silent state shifts.

  - **Scope:**
    - **In scope:**
      - Core behavioral interfaces and data models in package `io.effects.recipes.approvable`:
        - `ApprovableRequest`: Interface for purely behavioral, synchronous, non-anemic domain requests (no IO reference).
        - `ApprovalRecord`: Reusable, thread-safe domain state ledger representing the current request status, required authority level, and decision history.
        - `ApprovalDecision`: Represents an immutable entry in the audit trail.
        - `Status` and `DecisionType`: Enums representing state and action types.
        - `InitialAssessment` and `NextStep`: Result models for synchronous domain trials.
      - Infrastructure ports & adapters:
        - `ApprovalStateRepository` & `InMemoryApprovalStateRepository` (State persistence).
        - `ApprovalEventPublisher` & `InMemoryApprovalEventPublisher` (Event publication).
        - `ApprovalTelemetryPort` & `NoOpApprovalTelemetryPort` / `LoggingApprovalTelemetryPort` (System telemetry).
        - Events: `ApprovalEvent` (sealed interface) with `RequestSubmitted`, `RequestApproved`, `RequestRejected`, `RequestEscalated`.
      - Core Monadic Engine:
        - `ApprovalProcess`: The monadic orchestration engine. Exposes purely monadic APIs (returning lazy, concurrent `IO` blocks) to coordinate the message-routing, state lookup, evaluation of domain invariants, persistence, event emission, and telemetry.
      - Concrete Domain Instantiations (to prove recipe versatility):
        - `io.effects.recipes.approvable.ecommerce.ExpenseReport`: An e-commerce/FinTech instantiation where small amounts (< $100) auto-approve, medium amounts (< $1000) require `MANAGER` approval, and large amounts require `VP` approval.
        - `io.effects.recipes.approvable.healthcare.MedicalProcedureRequest`: A healthcare instantiation where routine procedures require `CLINICIAN` approval, but complex/surgical procedures require dual approval (`CHIEF_OF_SURGERY` + `INSURANCE_REP`).
      - Invariant, temporal, and idempotency tests in `io.effects.recipes.approvable.ApprovalRecipeTest`.
    - **Out of scope:**
      - Modifying existing monadic primitives (`IO.java`, `Either.java`, etc.) or files in the `reservable` package.

  - **Implementation Tasks:**
    - [x] **Investigate:**
      - *Analyzed ReservableResource, ResourceLedger, and ReservationProcess. Verified that domain trials are synchronous and pure, while orchestration is wrapped in the monadic IO block.*
      - *Reviewed ReservationRecipeTest for testing conventions, ensuring we test temporal policies, idempotency, and DI of ports.*
      - Analyze `io.effects.recipes.reservable.ReservableResource` and `io.effects.recipes.reservable.ReservationProcess` to ensure identical separation of synchronous domain trials (pure synchronous execution) from the monadic orchestration layer (IO-based effects and persistence).
      - Verify test conventions in `io.effects.recipes.reservable.ReservationRecipeTest`.
    - [x] **Implement:**
      - *Implemented core enums, models, pure domain request interface, and record/decision state ledger.*
      - *Created persistence, event, and telemetry ports with their in-memory/NoOp/Logging implementations.*
      - *Designed and built the ApprovalProcess manager wrapping state transitions, events, and telemetry inside the lazy IO monad.*
      - *Implemented real-world domains: ExpenseReport (FinTech) with multi-level amount-based routing, and MedicalProcedureRequest (Healthcare) with multi-step dual-approvals.*
      - Create domain models and enums in `@java-effects/lib/src/main/java/io/effects/recipes/approvable`:
        - Create `Status.java` and `DecisionType.java`.
        - Create `ApprovalDecision.java` as an immutable record of a single action step.
        - Create `ApprovalRecord.java` as the state ledger.
        - Create helper classes `InitialAssessment.java` and `NextStep.java`.
      - Create the core behavioral interface:
        - Create `ApprovableRequest.java`.
      - Create infrastructure ports, events, and adapters:
        - Create `ApprovalEvent.java` and its implementations (`RequestSubmitted`, `RequestApproved`, `RequestRejected`, `RequestEscalated`).
        - Create `ApprovalStateRepository.java` and `InMemoryApprovalStateRepository.java`.
        - Create `ApprovalEventPublisher.java` and `InMemoryApprovalEventPublisher.java`.
        - Create `ApprovalTelemetryPort.java`, `NoOpApprovalTelemetryPort.java`, and `LoggingApprovalTelemetryPort.java`.
      - Create the orchestration engine:
        - Create `ApprovalProcess.java` with monadic methods:
          - `register(String requestId, ApprovableRequest request)`: Adds a domain request to the process registry.
          - `submit(String requestId, Instant now)`: Evaluates initial submission and saves/publishes.
          - `approve(String requestId, String approverId, String approverRole, String comment, Instant now)`: Executes validation and transitions to next approval stage.
          - `reject(String requestId, String approverId, String approverRole, String reason, Instant now)`: Transitions to terminal rejected state.
          - `escalate(String requestId, String approverId, String approverRole, String targetAuthority, String reason, Instant now)`: Raises required authority.
      - Implement concrete domain classes:
        - Create `@java-effects/lib/src/main/java/io/effects/recipes/approvable/ecommerce/ExpenseReport.java` implementing `ApprovableRequest`.
        - Create `@java-effects/lib/src/main/java/io/effects/recipes/approvable/healthcare/MedicalProcedureRequest.java` implementing `ApprovableRequest`.
    - [x] **Test:**
      - *Created ApprovalRecipeTest.java under java-effects test sources.*
      - *Implemented and ran complete suite of tests for ExpenseReport flows, Healthcare Dual/Surgical workflows, Invariant checks (no actions on terminal states, authority validations, idempotency), and Custom Telemetry and DI capabilities.*
      - Create `@java-effects/lib/src/test/java/io/effects/recipes/approvable/ApprovalRecipeTest.java`.
      - Implement tests for the following:
        - **ExpenseReport Flow:** Auto-approval path, manager-approval path, VP-approval path, and rejection path.
        - **MedicalProcedure Dual Approval Flow:** Multi-step approval checking both clinician, chief, and rep requirements.
        - **Invariant Violations:** Assert that a rejected request cannot later be approved; assert that once approved, further updates are blocked or return idempotent success; assert that wrong authority roles are rejected.
        - **Audit Trail Integrity:** Assert that every decision step is recorded in the chronology and is immutable.
        - **Telemetry & Dependency Injection:** Inject custom telemetry and state repositories and assert correct execution.
    - [x] **Verify:**
      - *Successfully ran final compilation verification checks (./gradlew compileJava compileTestJava) and full test verification checks (./gradlew test).*
      - *All 100% of compilation and tests passed cleanly.*
      - Run `./gradlew compileJava compileTestJava` to ensure compilation is flawless and type-safe.
      - Run `./gradlew test` to execute all tests and verify all invariants, temporal laws, and idempotencies.
