# Implementation Plan: Next Generation Object Collaboration Recipes (Auditable, Entitleable, Meterable, Negotiable)
**Target Repository:** `java-effects`
**Reference Design:** `@java-effects/docs/oo_object_recipe_research_notes.md`

The following tickets break down the implementation of the next set of reusable business-level object collaboration recipes from the candidate catalog. They will be implemented inside the core `:lib` project of the `java-effects` repository.

All of these recipes will strictly leverage our root-level, generalized ports and adapters (`io.effects.ports.EventPublisher`, `StateRepository`, `TelemetryPort` and their respective generic `io.effects.adapters` implementations). They are designed with late-bound messaging, getter-free double-dispatch interfaces, and thread-safe rich aggregates!

---

- [x] **🎟️ [TICKET-001]: Implement the `Auditable` Object Collaboration Recipe**
  - **Description:** 
    Implement the `Auditable` recipe, representing a reusable, domain-agnostic protocol for managing cryptographically secured audit trails, point-in-time state replays, and explanatory auditing lookups.
    
    **Underlying Laws & Invariants of the Algebra:**
    1. *Immutability:* Once recorded, an audit entry cannot be deleted, modified, or reordered.
    2. *Reconstructibility:* An aggregate state representation `S` at time `T` must be perfectly reconstructible by sequentially replaying history up to `T`.
    3. *Explainability:* Specific historical decisions (represented by a step ID) must expose an explanatory metadata resolution via double-dispatch.
    4. *Snapshot Optimizability:* Taking a state snapshot of type `S` at time `T` must store the compact state and clear historical memory back to `T` to optimize memory footprints.
    
  - **Scope:**
    - **In scope:**
      - Interfaces, records, aggregates, and processes in package `io.effects.recipes.auditable`:
        - `AuditableRequest`: Pure behavioral synchronous domain interface representing an asset to be audited, defining the state reconstruction fold function.
        - `AuditLedger`: Reusable thread-safe domain state aggregate tracking audit history and active states.
        - `AuditStep`: Record tracking an individual audit step, including details of type `E`.
        - `AuditableProcess`: Monadic process manager (infrastructure engine) executing in `IO` to orchestrate record, replay, explain, and snapshot methods.
      - Event classes in `io.effects.recipes.auditable`:
        - `AuditRecorded`, `SnapshotTaken` (implementing `io.effects.recipes.auditable.AuditableEvent`).
    - **Out of scope:**
      - Integrating with OS-level secure enclaves or hardware security modules (HSMs).

  - **Implementation Tasks:**
    - [x] Create `@java-effects/lib/src/main/java/io/effects/recipes/auditable/AuditStep.java` containing:
      - *Created AuditStep record with generic type parameter `<E>`, including step details and a cryptographically linked SHA-256 hash linking steps.*
      - Add type parameter `<E>`.
      - Store step details of generic type `E` and a cryptographic SHA-256 string hash linking it to previous steps.
    - [x] Create `@java-effects/lib/src/main/java/io/effects/recipes/auditable/AuditableRequest.java` containing:
      - *Created AuditableRequest interface with generic parameters `<ID, E, S>`, and declared pure behavioral evaluateEntry, reconstructState, and explainDecision methods.*
      - Add type parameters `<ID, E, S>`.
      - Define behavioral messages:
        - `Either<String, Void> evaluateEntry(AuditLedger<ID, E> ledger, E detail, Instant now);`
        - `S reconstructState(List<AuditStep<E>> history);`
        - `String explainDecision(List<AuditStep<E>> history, String decisionStepId);`
    - [x] Create `@java-effects/lib/src/main/java/io/effects/recipes/auditable/AuditLedger.java` containing:
      - *Created AuditLedger aggregate root class with generic parameters `<ID, E>`, providing thread safety and a double-dispatch `recordEntry` step transaction, with SHA-256 links.*
      - Add type parameters `<ID, E>`.
      - Store `ID assetId`, `List<AuditStep<E>> history`.
      - Maintain thread safety and state encapsulation, delegating validations via double-dispatch.
    - [x] Create `@java-effects/lib/src/main/java/io/effects/recipes/auditable/AuditableProcess.java` containing:
      - *Created AuditableProcess and AuditableEvent supporting generic state replay, cryptographic audit logging, and point-in-time snapshotting.*
      - Monadic pipelines executing in `IO` for `record()`, `replay()`, `explain()`, and `snapshot()`.
    - [x] Create `@java-effects/lib/src/test/java/io/effects/recipes/auditable/AuditableRecipeTest.java` containing:
      - *Created AuditableRecipeTest verifying cryptographic hashing chains, sequential state replays, points-of-time explanations, and snapshot compaction.*
      - Define a custom `State` record and `AuditEvent` record.
      - Implement tests proving that replaying step histories correctly folds them into matching states, and asserting cryptographic hashing chains.

- [ ] **🎟️ [TICKET-002]: Implement the `Entitleable` Object Collaboration Recipe**
  - **Description:** 
    Implement the `Entitleable` recipe, representing a reusable, domain-agnostic protocol for managing user permissions, capability grants, dynamic revocations, and explanatory checks.
    
    **Underlying Laws & Invariants of the Algebra:**
    1. *Grant Expiry:* Entitlement grants can be bound to temporal expirations, after which checks automatically return a denial.
    2. *Scope Invariant:* A check request with a specific target context is allowed only if the actor possesses a grant enclosing that scope.
    3. *Grant Revocation:* Once revoked, subsequent entitlement checks on the actor's grant immediately return a denial.
    4. *Explainability:* Checking an entitlement must provide a descriptive explanation payload detailing why access was allowed or denied.
    
  - **Scope:**
    - **In scope:**
      - Interfaces, records, aggregates, and processes in package `io.effects.recipes.entitleable`:
        - `EntitleableRequest`: Pure behavioral synchronous domain interface representing a clearance rule context.
        - `EntitlementLedger`: Reusable thread-safe domain state aggregate tracking active grants and histories.
        - `EntitlementStep`: Record tracking a grant or revoke action, including grant details of type `G`.
        - `EntitleableProcess`: Monadic process manager executing in `IO` to orchestrate grants, checks, and revocations.
      - Event classes in `io.effects.recipes.entitleable`:
        - `EntitlementGranted`, `EntitlementRevoked`, `EntitlementChecked`.
    - **Out of scope:**
      - Integrating with Keycloak, Active Directory, or OAuth 2.0 servers.

  - **Implementation Tasks:**
    - [ ] Create `@java-effects/lib/src/main/java/io/effects/recipes/entitleable/EntitlementStep.java` containing:
      - Add type parameter `<G>`.
      - Store `G grant`, `String stepId`, `Type type`, and timestamp.
    - [ ] Create `@java-effects/lib/src/main/java/io/effects/recipes/entitleable/EntitleableRequest.java` containing:
      - Add type parameters `<ID, G, C>`.
      - Define behavioral messages:
        - `Either<String, Void> evaluateGrant(EntitlementLedger<ID, G> ledger, G grant, Instant now);`
        - `Either<String, Void> evaluateCheck(EntitlementLedger<ID, G> ledger, G grant, C context, Instant now);`
    - [ ] Create `@java-effects/lib/src/main/java/io/effects/recipes/entitleable/EntitlementLedger.java` containing:
      - Add type parameters `<ID, G>`.
      - Store `ID actorId`, `ConcurrentMap<String, EntitlementStep<G>> activeGrants`, and `List<EntitlementStep<G>> history`.
      - Maintain thread safety and active grant maps, executing double-dispatch.
    - [ ] Create `@java-effects/lib/src/main/java/io/effects/recipes/entitleable/EntitleableProcess.java` containing:
      - Monadic pipelines executing in `IO` for `grant()`, `revoke()`, and `check()`.
    - [ ] Create `@java-effects/lib/src/test/java/io/effects/recipes/entitleable/EntitleableRecipeTest.java` containing:
      - Define a custom `Clearance` grant record and `AccessContext` record.
      - Implement tests asserting grant bounds, temporal grant expirations, dynamic revocations, and access check explanations.

- [ ] **🎟️ [TICKET-003]: Implement the `Meterable` Object Collaboration Recipe**
  - **Description:** 
    Implement the `Meterable` recipe, representing a reusable protocol for consumption usage tracking, dynamic tier plan rating, and invoicing.
    
    **Underlying Laws & Invariants of the Algebra:**
    1. *Chronological Consumption:* Discrete usage ticks of type `U` can be appended at any time during an active billing cycle.
    2. *Billing Cycle Finality:* Once a meter is rated and billed, that billing cycle is finalized and subsequent usage ticks are automatically routed to the next cycle.
    3. *Rating Plan Late Binding:* The rating scheme and plan is completely late-bound, allowing the consumer's request implementation to calculate pricing dynamically over the usage history.
    
  - **Scope:**
    - **In scope:**
      - Interfaces, records, enums, aggregates, and processes in package `io.effects.recipes.meterable`:
        - `MeterableRequest`: Pure behavioral synchronous domain interface representing a billing scheme.
        - `MeterLedger`: Reusable thread-safe domain state aggregate tracking usage history.
        - `UsageStep`: Record tracking a discrete consumption tick of type `U`.
        - `MeterableProcess`: Monadic process manager executing in `IO` to orchestrate starts, ticks, and ratings.
      - Event classes in `io.effects.recipes.meterable`:
        - `MeterStarted`, `UsageRecorded`, `MeterRated`.
    - **Out of scope:**
      - Connecting directly to payment gateways (Stripe, Adyen) or PDF invoice generation.

  - **Implementation Tasks:**
    - [ ] Create `@java-effects/lib/src/main/java/io/effects/recipes/meterable/UsageStep.java` containing:
      - Add type parameter `<U>`.
      - Store `U metric` tick details.
    - [ ] Create `@java-effects/lib/src/main/java/io/effects/recipes/meterable/MeterableRequest.java` containing:
      - Add type parameters `<ID, U, R>`.
      - Define behavioral messages:
        - `Either<String, Void> evaluateUsage(MeterLedger<ID, U> ledger, U metric, Instant now);`
        - `Either<String, R> evaluateRating(MeterLedger<ID, U> ledger, Instant now);`
    - [ ] Create `@java-effects/lib/src/main/java/io/effects/recipes/meterable/MeterLedger.java` containing:
      - Add type parameters `<ID, U>`.
      - Maintain `ID accountId`, `Status status`, and `List<UsageStep<U>> history`.
      - Implement thread-safe transitions and billing cycle locks.
    - [ ] Create `@java-effects/lib/src/main/java/io/effects/recipes/meterable/MeterableProcess.java` containing:
      - Monadic pipelines executing in `IO` for `start()`, `recordUsage()`, and `rate()`.
    - [ ] Create `@java-effects/lib/src/test/java/io/effects/recipes/meterable/MeterableRecipeTest.java` containing:
      - Define a custom `ApiUsage` metric tick record and `BillInvoice` rated record.
      - Implement tests verifying continuous usage ticks, dynamic tier calculation (SaaS rates), cycle transitions, and post-billing finality locks.

- [ ] **🎟️ [TICKET-004]: Implement the `Negotiable` Object Collaboration Recipe**
  - **Description:** 
    Implement the `Negotiable` recipe, representing a reusable, domain-agnostic protocol for multi-party bargaining, offer-matching, and counter-proposals.
    
    **Underlying Laws & Invariants of the Algebra:**
    1. *Turn Invariant:* Counter-proposals can only be submitted by parties different from the sender of the active offer.
    2. *Bargaining Bounds:* Offers must comply with active boundaries (e.g. price bands or deadline terms) checked dynamically via double-dispatch.
    3. *Bargaining Finality:* Once accepted or withdrawn, the negotiation session is terminal, and further proposals are blocked.
    
  - **Scope:**
    - **In scope:**
      - Interfaces, records, aggregates, and processes in package `io.effects.recipes.negotiable`:
        - `NegotiableRequest`: Pure behavioral synchronous domain interface representing negotiation terms.
        - `NegotiationLedger`: Reusable thread-safe domain state aggregate tracking active status and proposal histories.
        - `NegotiationStep`: Record tracking a proposal transition of type `P`.
        - `NegotiableProcess`: Monadic process manager executing in `IO` to orchestrate session offers, counters, acceptances, and withdrawals.
      - Event classes in `io.effects.recipes.negotiable`:
        - `OfferMade`, `CounterOfferMade`, `NegotiationAccepted`, `NegotiationWithdrawn`.
    - **Out of scope:**
      - Secure email messaging systems or legal contract signature verification.

  - **Implementation Tasks:**
    - [ ] Create `@java-effects/lib/src/main/java/io/effects/recipes/negotiable/NegotiationStep.java` containing:
      - Add type parameter `<P>`.
      - Store `P proposal`, `String stepId`, `String actorId`, `Type type`, and timestamp.
    - [ ] Create `@java-effects/lib/src/main/java/io/effects/recipes/negotiable/NegotiableRequest.java` containing:
      - Add type parameters `<ID, P>`.
      - Define behavioral messages:
        - `Either<String, Void> evaluateOffer(NegotiationLedger<ID, P> ledger, String actorId, P proposal, Instant now);`
        - `Either<String, Void> evaluateCounter(NegotiationLedger<ID, P> ledger, String actorId, P proposal, Instant now);`
        - `Either<String, Void> evaluateAcceptance(NegotiationLedger<ID, P> ledger, String actorId, Instant now);`
    - [ ] Create `@java-effects/lib/src/main/java/io/effects/recipes/negotiable/NegotiationLedger.java` containing:
      - Add type parameters `<ID, P>`.
      - Store `ID sessionId`, `Status status`, and `List<NegotiationStep<P>> history`.
      - Implement thread safety and turn validations, delegating domain checks to `NegotiableRequest`.
    - [ ] Create `@java-effects/lib/src/main/java/io/effects/recipes/negotiable/NegotiableProcess.java` containing:
      - Monadic pipelines executing in `IO` for `makeOffer()`, `makeCounter()`, `accept()`, and `withdraw()`.
    - [ ] Create `@java-effects/lib/src/test/java/io/effects/recipes/negotiable/NegotiableRecipeTest.java` containing:
      - Define a custom `ContractOffer` proposal record.
      - Implement tests asserting sequential turn invariants, price band enforcement, acceptance finalities, and withdrawal cancellations.
