# Reusable Object Collaboration Recipes for Object-Oriented Systems

**Purpose:** Research notes for designing and implementing reusable object-oriented building blocks that act like business-facing recipes. These recipes allow a consumer to describe a business problem in domain language while hiding the internal collaboration among objects.

**Intended use:** This file is written to ground planning agents, implementation agents, and research agents. Agents should treat the material as conceptual scaffolding for building a recipe catalog, a recipe specification format, a runtime implementation model, and one or more prototype recipes.

**Core thesis:** Object-oriented reuse should not be limited to reusable classes or inheritance hierarchies. The deeper reusable unit is a **message-based collaboration protocol**: a named set of object roles, messages, laws, state transitions, policies, capabilities, events, and process rules that can be specialized to many business domains.

---

## 1. Research framing

The goal is to identify foundational object patterns that serve as reusable building blocks for OO projects. These patterns should behave like **recipes**:

- The business user or application designer describes the problem in business language.
- The recipe identifies the required object roles.
- The recipe hides the internal collaboration details.
- The implementation adapts the recipe to the concrete business context.
- The result is reusable design knowledge, not merely reusable code.

A useful analogy comes from functional programming. In FP, typeclasses and algebraic abstractions such as functor, applicative, monad, monoid, foldable, and traversable allow programmers to write core business logic as pure functions while separating effects and environment handling. The OO equivalent should not simply imitate monads. Instead, it should identify **object collaboration algebras** that define what messages objects understand, what laws those messages obey, and how objects coordinate with each other while protecting their internal state.

The target is therefore not a catalog of GoF design patterns. It is a catalog of **business-problem object algebras**.

---

## 2. Alan Kay grounding: OOP as messaging, state-process, and late binding

This research should be grounded in Alan Kay's original view of object-oriented programming.

Kay did not primarily mean "classes plus inheritance". His view was closer to a network of independent cells or small computers that communicate through messages. The important ideas were:

1. Objects communicate by **messages**.
2. Objects retain and protect their own **state-process**.
3. The internals of an object are hidden from other objects.
4. Binding is late, flexible, and extensible.
5. Objects can have multiple algebras or families of generic behavior.

A compact formulation from Kay's 2003 email is:

> OOP means messaging, local retention and protection and hiding of state-process, and extreme late-binding.

For this research, that implies the reusable unit should not be a class definition alone. The reusable unit should be a **protocol of interaction among protected stateful participants**.

### Implication for this project

Instead of asking:

```text
What base classes should all business objects inherit from?
```

ask:

```text
What recurring message protocols appear across business domains?
What object roles participate in those protocols?
What laws must always hold?
What details should be hidden behind the recipe boundary?
```

---

## 3. OO equivalent of FP typeclasses

In FP, a typeclass says:

```text
For any type that supports these operations and obeys these laws,
generic code can use it.
```

The OO equivalent proposed here is:

```text
For any object ecology that supports these roles, messages, and laws,
a reusable business collaboration can be instantiated.
```

Call this a **protocol algebra**.

### Definition: Protocol algebra

A protocol algebra is a named set of:

- Object roles.
- Valid messages.
- Message preconditions and postconditions.
- State transitions.
- Invariants and laws.
- Required policies.
- Required capability objects for effects.
- Events emitted by the collaboration.
- Composition rules with other protocol algebras.

Example:

```text
Reservable
  Roles:
    Resource, Hold, Reservation, AvailabilityPolicy, Clock, Scheduler

  Messages:
    quote, hold, confirm, release, expire

  Laws:
    confirm requires a valid hold
    expired hold cannot be confirmed
    release is idempotent
    held + confirmed capacity must not exceed total capacity
```

This is analogous to an FP typeclass because it defines generic behavior plus laws. It is more OO because it is not only about functions over values. It is about objects, messages, local state, time, effects, and collaboration.

---

## 4. Important distinction: class, role, protocol, recipe

Use these terms consistently.

| Term | Meaning | Reuse level |
|---|---|---|
| Class | Concrete implementation template for objects | Code-level reuse |
| Interface | Set of operations an object exposes | API-level reuse |
| Role | Responsibility an object plays in a collaboration | Design-level reuse |
| Protocol | Legal message sequence and semantics for a role | Behavioral reuse |
| Algebra | Protocol plus laws and composition rules | Formal reusable abstraction |
| Recipe | Business-facing package of roles, protocols, laws, and hidden collaboration | Product/design reuse |

A recipe may involve many roles. Each role may be implemented by one or more classes. A single object may play several roles. A single class may be used in several recipes.

Therefore, the recipe catalog should avoid being tied too early to class hierarchies.

---

## 5. Foundational OO building blocks

These are the primitive ingredients that will recur inside recipes.

| Building block | Core question it answers | FP analogy | OO interpretation |
|---|---|---|---|
| Message | What can be asked? | Function application | A request sent to an object, not direct state access |
| Protocol / Role | What messages are valid? | Typeclass | A named capability with laws |
| State-owner | Who protects invariants? | State transition function | Object that owns lifecycle and private state |
| Policy | Who decides? | Pure predicate or function | Replaceable business rule object |
| Process | Who coordinates over time? | Workflow / monadic composition | Long-running object that sequences messages |
| Capability / Port | Who performs effects? | Effect algebra / handler | Object representing external power: database, email, payment, clock |
| Event | What happened? | Immutable value / log entry | Published fact after a state transition |
| Projection | What view is useful? | Fold over history | Derived read object |
| Repository / Directory | How do objects find each other? | Environment / context | Object lookup by identity, query, or relationship |
| Adapter / Translator | How do worlds communicate? | Natural transformation-like mapping | Object that converts protocols across bounded contexts |
| Scheduler / Clock | How is time represented? | Temporal context | Time as an explicit collaborator, not a hidden global |
| Relationship object | How are objects associated? | Graph relation | First-class relationship with meaning, rules, and history |

### Design principle

Avoid hidden global effects. Treat time, storage, payment, notification, external APIs, and randomness as explicit collaborating objects.

---

## 6. Candidate foundational recipe families

The following families are business-level object collaboration patterns. They are not merely implementation patterns. They are recurring shapes of business behavior.

### 6.1 State and identity recipes

| Recipe | Business shape | Typical messages | Internal collaborators |
|---|---|---|---|
| Entity / Lifecycle | Something has identity and moves through states | create, activate, suspend, close, archive | State object, Policy, EventLog |
| Aggregate / Invariant Boundary | A cluster of objects must remain consistent | command, validate, commit | Child entities, Policy, Event publisher |
| Versioned Object / World-line | We need history, audit, undo, replay | atVersion, apply, rollback, snapshot | EventLog, Snapshot, Clock |
| Projection / Read Model | We need a view optimized for questions | refresh, query, explainSource | Source events, Projector, Index |

#### Notes

The versioned-object recipe is especially important. It provides the bridge between FP's stable immutable values and OO's protected state-process. An object can be treated as a **world-line**: a sequence of stable states over time. Events or commands produce the next stable state from the previous one.

This supports:

- Audit.
- Undo.
- Rollback.
- Replay.
- Debugging.
- Temporal queries.
- Deterministic process reasoning.

### 6.2 Decision and rule recipes

| Recipe | Business shape | Typical messages | Internal collaborators |
|---|---|---|---|
| Specification | Does this candidate satisfy a rule? | isSatisfiedBy, explain | Predicate objects, glossary terms |
| Policy | Which decision should be made under current context? | decide, rank, rejectReason | Rules, context, evidence |
| Strategy | Several algorithms can fulfill the same role | execute, estimate, supports | Strategy registry, selector |
| Interpreter / Business DSL | Users describe intent in domain language | parse, interpret, validate | Grammar, semantic model, executor |

#### Notes

Policies should be first-class objects, not scattered conditionals. Specifications and policies should support explanation because business users need to know why something was rejected, approved, ranked, priced, escalated, or routed.

### 6.3 Effect and boundary recipes

| Recipe | Business shape | Typical messages | Internal collaborators |
|---|---|---|---|
| Gateway / Port | Perform an external effect | send, charge, fetch, publish | Adapter, retry policy, credentials |
| Repository | Retrieve and persist state-owned objects | get, save, findBy | Storage adapter, identity map |
| Outbox / Reliable Publication | State change and event publication must not diverge | record, flush, confirm | Transaction, event bus, retry |
| Anti-corruption Layer | Two models use different meanings | translateIn, translateOut, reconcile | Mapper, glossary, validation policy |

#### Notes

This is the OO counterpart to FP effect separation. Instead of encoding effects in a monad, OO systems can represent external powers as **capability objects**. A domain object does not directly perform arbitrary I/O. It sends messages to explicit collaborators such as PaymentCapability, NotificationCapability, Clock, Repository, Scheduler, or EventPublisher.

### 6.4 Time and coordination recipes

| Recipe | Business shape | Typical messages | Internal collaborators |
|---|---|---|---|
| Reservation / Hold / Lease | A scarce thing can be temporarily claimed | quote, hold, confirm, expire, release | Resource, Hold, Clock, Scheduler, Policy |
| Saga / Process Manager | A business transaction spans many objects/systems | start, advance, compensate, timeout | Steps, state machine, outbox, gateways |
| Approval / Escalation | A decision needs one or more authorities | request, approve, reject, escalate | Approver, Policy, Clock, Notification |
| Subscription / Observer | React when something changes | subscribe, notify, unsubscribe | Event source, subscriber, filter |
| Scheduler / Timeout | Something must happen later | schedule, cancel, fireDue | Clock, queue, process manager |

#### Notes

Time should be modeled explicitly. Hidden calls to system time make collaboration hard to test and reason about. Most business protocols involve time: expiry, deadlines, aging, escalation, effective dates, settlement windows, retry intervals, service-level agreements, and grace periods.

### 6.5 Composition and relationship recipes

| Recipe | Business shape | Typical messages | Internal collaborators |
|---|---|---|---|
| Composite | Treat many things as one thing | add, remove, total, traverse | Children, policy, visitor |
| Catalog / Directory | Discover available objects/capabilities | register, lookup, describe | Metadata, capability descriptors |
| Relationship Object | The relation itself has meaning | connect, disconnect, qualify, expire | Endpoint objects, relationship policy |
| Negotiation / Matchmaking | Objects find compatible collaborators | offer, request, match, accept | Descriptions, constraints, matcher |

#### Notes

Relationships are often not just foreign keys. A relationship can have:

- Its own identity.
- Its own lifecycle.
- Effective dates.
- Rules.
- Audit history.
- Roles at each endpoint.
- Qualification or confidence.

For example, employment, membership, ownership, entitlement, contract participation, and dependency are better modeled as relationship objects than as simple attributes.

---

## 7. Recipe specification format

A reusable OO recipe should be specified above code. The specification should help agents reason about the business meaning, object roles, collaboration details, laws, and implementation obligations.

Recommended structure:

```text
Recipe Name

Business intent:
  What recurring business situation does this recipe solve?

Problem statement grammar:
  How can a consumer describe the problem in domain language?

Applicable when:
  What signs indicate this recipe fits?

Not applicable when:
  What situations should use another recipe?

Roles:
  Named object responsibilities.

Messages:
  Messages accepted by each role.

State model:
  States, transitions, terminal states, invalid transitions.

Laws / invariants:
  Rules that must always hold.

Policies:
  Replaceable decision objects.

Capabilities / effects:
  External powers required by the recipe.

Events:
  Facts emitted by successful or failed transitions.

Hidden collaboration:
  Internal message flow hidden from the business consumer.

Composition:
  Other recipes commonly used with this one.

Implementation notes:
  Suggested object model, persistence, concurrency, testing.

Examples:
  Domain-specific instantiations.

Evaluation questions:
  How do we know the recipe was implemented correctly?
```

---

## 8. Fully worked seed recipe: Reservation / Hold / Lease

This is probably the best first recipe to formalize and prototype because it appears across many domains.

### 8.1 Business intent

A scarce resource can be temporarily held for an actor, then either confirmed, released, expired, rejected, or moved to a waitlist.

### 8.2 Business problem grammar

```text
Actor reserves Resource for TimeWindow under Policy.
Actor holds Resource until ExpiryTime.
Actor confirms Hold into Reservation.
System expires Hold after ExpiryTime.
System rejects request when Policy cannot satisfy it.
```

Optional extensions:

```text
Actor joins Waitlist when Resource is unavailable.
Payment must be authorized before confirmation.
Notification must be sent after confirmation or expiry.
Reservation can be cancelled under CancellationPolicy.
```

### 8.3 Applicable when

Use this recipe when:

- The business has scarce capacity or inventory.
- A request should not immediately become a permanent commitment.
- Temporary claims need expiry.
- Confirmation may require additional steps.
- Overbooking, duplicate booking, or race conditions are risks.

### 8.4 Not applicable when

Do not use this recipe when:

- There is no scarcity.
- Requests are instantly fulfilled and cannot conflict.
- There is no meaningful distinction between intent, hold, and confirmation.
- Capacity can be computed lazily without protected state or temporal rules.

### 8.5 Roles

| Role | Responsibility |
|---|---|
| Actor | Person, system, or organization requesting the reservation |
| Resource | Scarce thing or capacity being reserved |
| Hold | Temporary claim on the resource |
| Reservation | Confirmed claim |
| AvailabilityPolicy | Decides whether capacity can be held |
| ConfirmationPolicy | Decides whether hold can become reservation |
| CancellationPolicy | Decides whether confirmed reservation can be cancelled |
| Clock | Provides current time |
| Scheduler | Schedules expiry |
| Repository | Persists holds and reservations |
| EventPublisher | Publishes facts about state changes |
| PaymentCapability | Optional effect collaborator for authorization/capture/refund |
| NotificationCapability | Optional effect collaborator for messages to users |

### 8.6 Messages

```text
quote(resource, window, actor, context)
hold(resource, window, actor, context)
confirm(holdId, context)
release(holdId, reason)
expire(holdId)
cancel(reservationId, reason)
status(holdOrReservationId)
explainRejection(request)
```

### 8.7 State model

```text
Requested
  -> Quoted
  -> Held
  -> Confirmed
  -> Released
  -> Expired
  -> Rejected
  -> Cancelled
```

Possible transitions:

```text
Requested -> Quoted
Requested -> Rejected
Quoted -> Held
Held -> Confirmed
Held -> Released
Held -> Expired
Confirmed -> Cancelled
```

Invalid transitions:

```text
Expired -> Confirmed
Released -> Confirmed
Rejected -> Confirmed
Cancelled -> Confirmed
Confirmed -> Held
```

### 8.8 Laws and invariants

```text
A confirmed reservation must correspond to a valid hold.
A hold expires after its expiry time.
An expired hold cannot be confirmed.
A released hold cannot be confirmed.
Release is idempotent.
Expire is idempotent.
Capacity held plus capacity confirmed must not exceed total capacity.
A hold must have an owner actor.
A hold must have a resource identity.
A hold must have an expiry time.
Confirmation must preserve the original resource claim unless policy explicitly allows substitution.
Duplicate confirmation with the same idempotency key returns the same result.
```

Payment-specific laws:

```text
Capture amount cannot exceed authorized amount.
A failed payment capture prevents confirmation unless policy allows deferred settlement.
Refund amount cannot exceed captured amount.
Payment side effects must be idempotent by request key.
```

Notification-specific laws:

```text
Notification failure must not silently roll back a confirmed reservation unless policy requires atomic notification.
Notifications should be derived from events where possible.
```

### 8.9 Hidden collaboration

The business consumer says:

```text
Reserve appointment slot for patient on Friday at 10:00.
```

The recipe internally performs something like:

```text
ReservationService receives reserve request
  -> ResourceDirectory locates appointment slot resource
  -> AvailabilityPolicy checks capacity
  -> Clock provides current time
  -> Hold object is created with expiry
  -> Repository stores hold
  -> Scheduler schedules expiry message
  -> EventPublisher publishes HoldCreated
  -> Response returns hold details
```

For confirmation:

```text
ReservationService receives confirm request
  -> Repository loads hold
  -> Hold validates not expired/released
  -> ConfirmationPolicy checks context
  -> Optional PaymentCapability captures payment
  -> Resource confirms capacity
  -> Reservation object is created
  -> Repository stores reservation
  -> EventPublisher publishes ReservationConfirmed
  -> Optional NotificationCapability sends confirmation
```

### 8.10 Events

```text
ReservationQuoted
HoldCreated
HoldRejected
HoldConfirmed
HoldReleased
HoldExpired
ReservationCreated
ReservationCancelled
ReservationRejected
PaymentAuthorizationRequested
PaymentCaptured
PaymentFailed
NotificationRequested
NotificationSent
```

### 8.11 Domain instantiations

| Domain | Resource | Actor | Hold | Confirmation |
|---|---|---|---|---|
| Hotel | Room-night inventory | Guest | Room hold | Booking |
| Healthcare | Appointment slot | Patient | Appointment hold | Scheduled visit |
| E-commerce | Inventory unit | Shopper | Cart reservation | Order |
| Logistics | Delivery capacity | Shipper | Capacity hold | Shipment booking |
| Finance | Credit amount | Borrower | Credit offer | Accepted loan |
| Education | Seat in course | Student | Enrollment hold | Enrollment |
| Ticketing | Seat or admission capacity | Buyer | Ticket hold | Ticket purchase |

### 8.12 Suggested implementation shape

```text
ReservableResource
  - resourceId
  - capacityModel
  - availabilityState
  - hold(request)
  - confirm(hold)
  - release(hold)

Hold
  - holdId
  - actorId
  - resourceId
  - quantity/window
  - status
  - expiresAt
  - confirm()
  - release()
  - expire()

Reservation
  - reservationId
  - holdId
  - actorId
  - resourceId
  - confirmedAt
  - status

AvailabilityPolicy
  - canHold(resource, request, context)
  - rejectionReason(...)

ReservationProcess
  - reserve(...)
  - confirm(...)
  - cancel(...)
```

### 8.13 Tests

```text
Holding available capacity creates a hold.
Holding unavailable capacity returns rejection with explanation.
Expired hold cannot be confirmed.
Releasing a hold twice is safe.
Confirming a hold twice with same idempotency key returns same reservation.
Concurrent holds cannot exceed capacity.
Scheduler expiry transitions held hold to expired.
Payment failure prevents confirmation when policy requires payment.
Event log contains hold and confirmation facts.
```

---

## 9. Candidate catalog of business-level object algebras

This is a starting set for the recipe catalog.

| Algebra | Business meaning | Core messages |
|---|---|---|
| Ownable | Something belongs to someone | assignOwner, transferOwner, revokeOwner |
| Reservable | Something scarce can be held | quote, hold, confirm, release, expire |
| Payable / Settleable | Obligation can be settled | authorize, capture, refund, settle |
| Approvable | Action needs permission | request, approve, reject, escalate |
| Fulfillable | Promise becomes delivery | promise, allocate, dispatch, complete |
| Schedulable | Action occurs in time | schedule, reschedule, cancel, fire |
| Observable | Change can be noticed | subscribe, publish, notify |
| Auditable | History must be preserved | record, replay, explain, snapshot |
| Combinable | Many things act as one | add, remove, compose, total |
| Transformable | One representation becomes another | translate, validate, map |
| Reconciliable | Two states must be matched | compare, match, resolve, compensate |
| Entitleable | Actor has rights or capabilities | grant, revoke, check, explain |
| Meterable | Usage can be measured | start, recordUsage, rate, bill |
| Negotiable | Parties converge on agreement | offer, counter, accept, withdraw |
| Classifiable | Object receives semantic meaning | tag, classify, explain, reclassify |
| Routable | Work must be directed to a handler | route, assign, reroute, reject |
| Prioritizable | Work competes for attention | rank, reprioritize, defer, expedite |
| Retryable | Failed work can be attempted again | attempt, retry, backoff, abandon |
| Compensable | Completed step can be semantically undone | compensate, reverse, settleDifference |
| Claimable | Actor asserts a right or fact | claim, dispute, verify, accept, deny |

---

## 10. Example laws for selected algebras

### 10.1 Reservable laws

```text
confirm(x) is valid only after hold(x).
expire(x) prevents confirm(x).
release(x) is idempotent.
held capacity + confirmed capacity <= total capacity.
A hold must have an expiry policy.
```

### 10.2 Payable laws

```text
capture amount <= authorized amount.
refund amount <= captured amount.
settlement eventually reaches a terminal state.
duplicate capture with same idempotency key has same result.
failed authorization cannot be captured.
```

### 10.3 Approvable laws

```text
A rejected request cannot later be approved without reopening.
Escalation preserves audit history.
Approval authority must satisfy policy at approval time.
A request must expose enough evidence for decision.
Approval decision should be explainable.
```

### 10.4 Fulfillable laws

```text
A fulfillment must correspond to a promise or order.
Completion requires all required fulfillment steps to reach terminal success or accepted exception.
Cancellation after dispatch requires compensation or return flow.
Partial fulfillment must be visible in state.
```

### 10.5 Auditable laws

```text
Recorded events are append-only.
Event order is stable within an aggregate or process boundary.
Replay from initial state plus event stream reconstructs derived state.
Corrections are represented by new events, not mutation of old events.
```

### 10.6 Entitleable laws

```text
A check must be evaluated against the actor, resource, action, and context.
Revocation prevents future grants from being inferred unless a new grant exists.
Entitlement decisions should be explainable.
Delegated authority must not exceed delegator authority.
```

---

## 11. Mapping FP effects to OO capabilities

In FP, a common shape is:

```text
pure business function
  -> effect description
  -> interpreter / handler
```

In this OO research, the corresponding shape is:

```text
domain object receives message
  -> consults policy objects
  -> protects or changes local state
  -> emits event or decision
  -> sends messages to explicit capability objects
```

### Separation of responsibilities

| Responsibility | OO object type |
|---|---|
| Owns invariants and business language | Domain object / Aggregate |
| Owns replaceable decisions | Policy / Specification |
| Owns external effects | Capability / Gateway / Port |
| Owns time and coordination | Process object / Scheduler / Clock |
| Records facts | Event / EventLog |
| Retrieves and persists objects | Repository / Object Directory |
| Adapts one model to another | Adapter / Anti-corruption object |

### Rule

Do not let side effects leak into arbitrary domain methods. Effects should pass through capability objects or be represented as events/commands that capability objects handle.

---

## 12. Avoid common OO failure modes

The common failure is to turn every noun into a data class and every verb into a service. That collapses OO into procedural programming over records.

Avoid:

```text
Anemic data objects
God services
Inheritance-first reuse
Direct state access
Getter/setter-driven design
Framework-specific base classes
Implicit time
Implicit I/O
Unlawed interfaces
Hidden global dependencies
```

Prefer:

```text
Objects as capability-bearing cells
Protocols over classes
Roles over inheritance
Messages over field access
Policies over scattered conditionals
Process objects over scattered orchestration
Events over invisible side effects
Laws over informal interface contracts
Explicit Clock, Scheduler, Repository, and Gateway collaborators
```

---

## 13. Recipe repository as an aspect-oriented knowledge object

For implementation, treat each recipe as a versioned, aspect-oriented knowledge object. This mirrors the idea that durable context and runtime prompt-fit context should be separated.

### Durable recipe aspects

```text
identity:
  recipe id, name, version, status, owner

business_intent:
  summary, business grammar, examples, non-examples

roles:
  role definitions, responsibilities, allowed substitutions

messages:
  message names, payloads, preconditions, postconditions

state_model:
  states, transitions, terminal states, invalid transitions

laws:
  invariants, algebraic laws, idempotency rules, ordering rules

collaboration:
  internal message flows, sequence diagrams, hidden object network

policies:
  decision points, replaceable policy contracts, explanation requirements

capabilities:
  required external powers, effect boundaries, idempotency requirements

events:
  facts emitted, event schema, consumers

composition:
  compatible recipes, dependency recipes, conflicting recipes

evidence:
  source notes, examples, rationale, implementation references
```

### Runtime recipe-fit aspects

When a user or agent brings a business problem, compute a runtime fit assessment:

```json
{
  "recipe_id": "reservable.hold_lease",
  "fit_score": 0.91,
  "covers": [
    "scarce resource",
    "temporary claim",
    "confirmation step",
    "expiry rule"
  ],
  "missing_context": [
    "capacity policy",
    "expiry duration",
    "payment requirement"
  ],
  "suggested_roles": {
    "Resource": "AppointmentSlot",
    "Actor": "Patient",
    "Hold": "AppointmentHold",
    "Reservation": "ScheduledVisit"
  },
  "recommended_next_steps": [
    "define resource identity and capacity model",
    "define availability policy",
    "choose expiry semantics",
    "decide whether payment or notification capabilities are required"
  ]
}
```

This makes the recipe catalog usable by agents. Agents can retrieve a recipe, assess fit against a business prompt, ask for missing information, and generate implementation plans.

---

## 14. Suggested agent workstreams

Use the following workstreams to implement this research incrementally.

### 14.1 Catalog-mining agent

Purpose: Identify recurring business verbs and group them into candidate algebras.

Inputs:

```text
Existing codebases
Domain documents
Event logs
User stories
Business process descriptions
DDD models
Workflow diagrams
```

Outputs:

```text
Business verb inventory
Candidate protocol algebras
Common role names
Common state transitions
Common invariants
Domain examples
```

### 14.2 Algebra-spec agent

Purpose: Turn a candidate algebra into a formal recipe specification.

Outputs:

```text
Recipe markdown
Role table
Message table
State model
Law list
Policy list
Capability list
Event list
Test obligations
```

### 14.3 Recipe-fit agent

Purpose: Given a business problem, identify which recipes apply.

Outputs:

```text
Fit scores
Matching signals
Missing context
Suggested object roles
Recipe composition plan
```

### 14.4 Implementation-planning agent

Purpose: Generate implementation tasks from a selected recipe.

Outputs:

```text
Object model
Interfaces/protocols
State transition tests
Policy contracts
Capability ports
Persistence model
Event schemas
Sequence diagrams
Backlog tasks
```

### 14.5 Law-testing agent

Purpose: Generate property tests, scenario tests, and concurrency tests from recipe laws.

Outputs:

```text
Invariant test suite
State transition tests
Idempotency tests
Race-condition tests
Effect-boundary tests
Contract tests for capabilities
```

### 14.6 Evidence and evaluation agent

Purpose: Track whether recipes are actually reusable and correct.

Outputs:

```text
Domains where recipe was used
Implementation variants
Defects prevented
Laws refined
Recipe changes by version
Reusable code generated
Manual design effort saved
```

---

## 15. Research roadmap

### Phase 1: Discover the primitive algebras

Collect recurring business verbs from real systems:

```text
reserve, approve, pay, fulfill, cancel, reconcile, classify,
notify, schedule, expire, audit, route, settle, transfer,
quote, assign, authorize, dispatch, compensate, retry
```

Group verbs into protocol algebras:

```text
Reservable, Approvable, Settleable, Fulfillable, Auditable,
Schedulable, Reconciliable, Entitleable, Meterable, Routable
```

### Phase 2: Define laws

For each algebra, specify:

```text
Ordering laws
Idempotency laws
Capacity laws
Consistency laws
Terminal-state laws
Compensation laws
Temporal laws
Authorization laws
Effect laws
```

### Phase 3: Identify roles

For each algebra, identify required object roles:

```text
State owner
Policy
Process manager
Capability
Repository
Event
Clock
Scheduler
Projection
Adapter
Relationship object
```

### Phase 4: Write recipe templates

Write recipes using business grammar, not code-first APIs.

Each recipe should answer:

```text
How does a business user describe the problem?
What objects must exist internally?
What messages flow among them?
What laws must hold?
What effects are required?
What should be configurable by policy?
```

### Phase 5: Prototype one seed recipe

Start with **Reservation / Hold / Lease**.

Implement it in at least three domains:

```text
Hotel room booking
Healthcare appointment scheduling
E-commerce inventory reservation
```

Evaluate whether the same protocol algebra works with domain-specific nouns, policies, and capabilities.

### Phase 6: Build a recipe catalog and fit engine

Represent each recipe as structured metadata plus markdown explanation.

The fit engine should:

```text
Parse a business prompt.
Identify signals such as scarcity, approval, payment, fulfillment, time, reconciliation.
Rank recipes by fit.
Suggest role mappings.
Ask for missing context.
Generate an implementation plan.
```

### Phase 7: Measure success by compression

The key success metric is not only code reuse. It is **design compression**.

Ask:

```text
How many business systems can be described as combinations of these recipes?
How much implementation planning can be generated from the recipe specification?
How many defects are prevented by recipe laws?
How much domain language can be preserved without exposing internal collaboration?
```

---

## 16. Implementation artifact: recipe schema draft

A possible machine-readable schema:

```yaml
recipe:
  id: reservable.hold_lease
  name: Reservation / Hold / Lease
  version: 0.1.0
  status: research-draft

business_intent:
  summary: A scarce resource can be temporarily held, confirmed, released, or expired.
  grammar:
    - Actor reserves Resource for TimeWindow under Policy.
    - Actor confirms Hold into Reservation.
    - System expires Hold after ExpiryTime.
  applicable_when:
    - Scarce capacity exists.
    - Temporary claim is required before commitment.
    - Expiry or timeout matters.
  not_applicable_when:
    - No scarcity exists.
    - Request is immediately fulfilled.

roles:
  Actor:
    responsibility: Requests and owns the hold or reservation.
  Resource:
    responsibility: Owns capacity and availability state.
  Hold:
    responsibility: Temporary claim with expiry.
  Reservation:
    responsibility: Confirmed claim.
  AvailabilityPolicy:
    responsibility: Decides whether hold can be created.
  Clock:
    responsibility: Supplies current time.
  Scheduler:
    responsibility: Sends expiry message later.

messages:
  - name: quote
    receiver: ReservationProcess
    preconditions: []
    postconditions:
      - Quote returned or rejection explained.
  - name: hold
    receiver: ReservationProcess
    preconditions:
      - Resource exists.
      - Actor is allowed to request hold.
    postconditions:
      - HoldCreated or HoldRejected event emitted.
  - name: confirm
    receiver: ReservationProcess
    preconditions:
      - Hold exists.
      - Hold is not expired.
      - Hold is not released.
    postconditions:
      - ReservationCreated or ReservationRejected event emitted.

laws:
  - id: reservable.confirm_requires_valid_hold
    statement: A confirmed reservation must correspond to a valid hold.
  - id: reservable.expired_hold_cannot_confirm
    statement: An expired hold cannot be confirmed.
  - id: reservable.release_idempotent
    statement: Releasing an already released hold returns the same terminal outcome.
  - id: reservable.capacity_never_exceeded
    statement: Held plus confirmed capacity must not exceed total capacity.

capabilities:
  required:
    - Repository
    - Clock
    - Scheduler
    - EventPublisher
  optional:
    - PaymentCapability
    - NotificationCapability

composition:
  commonly_combines_with:
    - payable.authorization_capture
    - notifiable.event_notification
    - auditable.event_log
    - approvable.approval_flow

tests:
  invariant_tests:
    - concurrent holds cannot exceed capacity
    - expired hold cannot be confirmed
  idempotency_tests:
    - release twice
    - confirm twice with same idempotency key
```

---

## 17. Questions for the next research iteration

Use these questions to guide further exploration.

### Conceptual questions

```text
What is the minimal basis set of object algebras for common business software?
Are some algebras primitive while others are compositions?
What laws distinguish one algebra from another?
Can recipes be composed without conflicting state ownership?
What is the OO equivalent of typeclass law checking?
```

### Implementation questions

```text
Should recipes generate interfaces, tests, state machines, or full object skeletons?
Should recipe roles be implemented as classes, traits, protocols, actors, or components?
How should recipes express effects without coupling to infrastructure frameworks?
How should recipes handle concurrency and distributed systems?
How should recipe laws become executable tests?
```

### Agent questions

```text
How does an agent detect that a business prompt implies a recipe?
How does an agent map domain nouns to recipe roles?
How does an agent ask for missing recipe parameters?
How does an agent decide between similar recipes?
How does an agent compose multiple recipes into a system design?
```

---

## 18. Key source anchors from uploaded research material

These notes are grounded in the uploaded Alan Kay and architecture materials.

### Alan-Kay-Email-Notes.txt

Key anchor:

```text
OOP as messaging, local retention/protection/hiding of state-process,
and extreme late-binding.
```

Use this as the philosophical foundation for recipes as message protocols rather than class hierarchies.

### Alan-Kay-Notes.txt

Key anchors:

```text
Objects as separate computers/servers.
Messaging as the big idea.
Loose coupling and late binding.
Higher-level languages reduce accidental complexity.
```

Use this to justify business-facing recipe grammars and hidden collaboration internals.

### Alan-Kays-view-on-Relationship-between-Functional-and-Object-oriented-Programmin.txt

Key anchors:

```text
Objects and functions are complementary, not opposed.
World-lines model histories of values, variables, data, and objects.
Stable states and functional transitions support debugging, undo, rollback, and deterministic relationships.
Loose coupling via description matching and negotiation of meaning is an advanced OO direction.
```

Use this to connect FP-style algebraic reasoning with OO state-process and temporal collaboration.

### Alan Kay Thesis: The Reactive Engine

Key anchors:

```text
FLEX as an interactive, extensible environment.
Bindings, attributes, and associations as core abstraction mechanisms.
Processes, coroutines, when-conditions, and user-extensible syntax.
Modeling environments where users express intent while the system handles internal machinery.
The NC example where the user states tooling intent and the system validates/simulates internally.
```

Use this as a precedent for business users expressing intent while recipe internals manage object collaboration.

### Agentic Data Architecture for Dataset Intelligence.pdf

Key anchor:

```text
Durable context should be computed and stored separately from prompt-time fit assessment.
Aspect-oriented objects are better than monolithic blobs when different aspects refresh independently.
```

Use this as an implementation analogy for recipe objects: durable recipe knowledge plus runtime recipe-fit assessments.

---

## 19. Working hypothesis

Most business software can be described as combinations of a small number of protocol algebras:

```text
ownership
reservation
approval
settlement
fulfillment
scheduling
observation
audit
classification
reconciliation
entitlement
metering
negotiation
routing
compensation
```

The research task is to prove or refine this hypothesis by repeatedly applying the algebras across domains.

The implementation task is to make these algebras concrete enough that an agent can:

```text
1. Read a business problem.
2. Select likely recipes.
3. Map business nouns to object roles.
4. Ask for missing policy/capability/state details.
5. Generate object collaboration plans.
6. Generate code skeletons and tests.
7. Validate the result against recipe laws.
```

---

## 20. Immediate next step

Formalize and prototype **Reservation / Hold / Lease**.

Minimum prototype scope:

```text
Domain 1: Healthcare appointment slot reservation
Domain 2: E-commerce inventory reservation
Domain 3: Hotel room-night reservation
```

For each domain:

```text
Map domain nouns to recipe roles.
Implement Resource, Hold, Reservation, Policy, Clock, Scheduler, Repository, EventPublisher.
Write law-based tests.
Compare how much code and design remains common.
Document which parts vary by policy, capability, or state model.
```

Expected learning:

```text
Whether the same protocol algebra survives across domains.
Which roles are essential and which are optional.
Which laws are universal and which are domain-specific.
How agents should ask for missing business context.
What implementation artifacts can be generated reliably.
```

---

## 21. Compact agent instruction block

Agents using this file should follow this operating model:

```text
Do not design from classes first.
Start from business verbs and business invariants.
Identify candidate protocol algebras.
Map domain nouns to recipe roles.
Make time, effects, policies, and relationships explicit.
Preserve object state-process boundaries.
Specify messages before fields.
Specify laws before implementation details.
Generate tests from laws.
Use capability objects for external effects.
Prefer composition of recipes over inheritance hierarchies.
```

---

## 22. Implemented recipes catalog reference

The following business-level collaboration recipes are fully implemented and verified in package `io.effects.recipes`:

1. **Reservable (`io.effects.recipes.reservable`):** Coordinates scarce resources, temporary leases/holds, and confirmations.
2. **Approvable (`io.effects.recipes.approvable`):** Manages multi-step authorization checks, supervisor approvals, rejections, and escalations.
3. **Ownable (`io.effects.recipes.ownable`):** Manages asset ownership assignment, validation, transfer, and revocation.
4. **Payable (`io.effects.recipes.payable`):** Coordinates financial transaction authorizations, captures, reversals, and refunds.
5. **Fulfillable (`io.effects.recipes.fulfillable`):** Manages multi-stage order allocation, packaging, dispatch, and delivery.
6. **Schedulable (`io.effects.recipes.schedulable`):** Manages timed triggers, reschedule adjustments, clock execution checks, and cancellations.

### Architectural Blueprint:
All recipes share a highly expressive, decoupled Ports and Adapters architecture:
- **Global, Generic Ports (`io.effects.ports.*`):** Unifies cross-cutting system capabilities: `EventPublisher<E>`, `StateRepository<K, V>`, and `TelemetryPort`.
- **Global, Reusable Adapters (`io.effects.adapters.*`):** Out-of-the-box system utilities: `InMemoryEventPublisher<E>`, `InMemoryStateRepository<K, V>`, `NoOpTelemetryPort`, and `LoggingTelemetryPort`.
- **Rich Aggregate Roots:** All business logic, enums, step records, state-process boundaries, and event generation reside entirely inside the state ledgers (`ResourceLedger`, `ApprovalRecord`, `OwnershipRecord`, `PaymentLedger`, `FulfillmentLedger`, and `ScheduleLedger`), rather than procedural process managers.
- **Getter-Free Behavioral Request Interfaces:** Consumer implementations (such as `ApprovableRequest` or `PayableRequest`) contain zero passive getters; all state queries are mediated synchronously via double dispatch, preventing state leaks.
- **Process Orchestrators:** The monadic process manager classes function strictly as thin infrastructure coordinators, sequencing persistence lookups and aggregate invocations in a concurrent, virtual-thread friendly `IO` context.

