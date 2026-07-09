# 🚀 expressj: Pure Object-Collaboration Domain Recipes for Java

`expressj` is a lightweight, high-performance, and language-native Java library of **10 non-anemic object-collaboration recipes** designed for rapid business prototyping. 

By enforcing a strict separation between a **Pure Synchronous Domain Core** and a **Monadic Lazy Shell**, `expressj` allows engineers to prototype, model, and verify complex business rules completely isolated from infrastructure, environments, databases, and side-effect distractions.

---

## 🏛️ Core Philosophy: Separating Domain from Distraction

In traditional Java enterprise architectures (e.g., Spring, Hibernate, Jakarta), business logic is typically implemented in "anemic" service layers. These service classes are tightly coupled to active databases, framework annotations, transaction managers, and asynchronous thread pools. This makes unit testing incredibly slow, introduces massive mock setup boilerplate (Mockito), and makes the core logic highly fragile.

`expressj` solves this by dividing your application into two distinct layers:

```text
       ┌────────────────────────────────────────────────────────┐
       │             MONADIC SHELL (Infrastructure)             │
       │                                                        │
       │  • Evaluates on Virtual Threads (Asynchronous/Lazy)   │
       │  • Manages StateRepositories, EventPublishers          │
       │  • Handles logging, telemetry and durations metrics    │
       └──────────────────────────┬─────────────────────────────┘
                                  │ (Atomic Double Dispatch)
                                  ▼
       ┌────────────────────────────────────────────────────────┐
       │             PURE DOMAIN CORE (Synchronous)             │
       │                                                        │
       │  • Pure deterministic functions returning Either<S,V>  │
       │  • Zero knowledge of SQL, frameworks, or concurrency    │
       │  • 100% testable in milliseconds without any mocks     │
       └────────────────────────────────────────────────────────┘
```

1. **The Pure Domain Core (Synchronous & Side-Effect Free):** Your business rules, state transitions, and validation invariants reside inside pure domain objects implementing the `expressj` request interfaces. These classes are 100% synchronous, getter-free, and side-effect-free. They do not know where data is stored or how threads are managed; they simply evaluate incoming proposals against a chronological state ledger and return deterministic `Either<String, Result>` values.
2. **The Monadic Shell (Lazy, Concurrent & Port-Aware):** The framework's process managers (e.g., `PayableProcess`, `ReservationProcess`) coordinate database lookups, thread shifts, event publications, and metric recordings. They wrap these side-effects inside a lightweight, lazy monadic `IO` shell, guaranteeing atomic state updates and strict referential transparency.

---

## 🌟 Featured Recipe Deep Dives

To see how `expressj` separates concerns while maintaining massive adaptability across entirely different business domains, let's explore two of our most widely applicable recipes: `Approvable` and `Reservable`.

---

### 🏛️ Recipe 1: `Approvable` (Workflow Authorization & Escalation)

The `Approvable` recipe coordinates any multi-step validation workflow where a request, document, or transaction requires review, routing, sequential approvals, escalations, or rejections from various organizational roles.

#### What the Framework Orchestrates (Infrastructure)
* **Monadic State Management:** Safely reads/writes the current state of an `ApprovalRecord` from the injected `StateRepository` inside lazy `IO` computations.
* **Chronological Ledgers:** Maintains a sequential, immutable audit history of every submission, approval, rejection, and escalation comment.
* **Event Publication:** Emits rich occurrences (`RequestSubmitted`, `RequestApproved`, `RequestRejected`, `RequestEscalated`) only upon successful state commits.
* **Telemetry Ports:** Records operational successes, failures, and execution latencies automatically.

#### What the Consumer Controls (Pure Business Logic)
The developer implements the pure `ApprovableRequest<ID, A, C>` interface:
* `evaluateInitialSubmission(Instant now)`: Evaluates a new request. You write pure logic to decide if the item is instantly `APPROVED` (auto-approval path) or starts as `PENDING` with a specific initial authority role (`A`) required.
* `evaluateDecision(record, approverId, approverRole, decisionType, comment, now)`: Evaluates a review action. You control the state machine by deciding if the approver has the correct authority, if the request is fully finalized, or if it must be **escalated** to a higher authority (e.g., Escalating `MANAGER` ➔ `CFO`).

#### Multi-Domain Adaptability Examples
* **FinTech (Corporate Expense Routing):**
  - **Authority (`A`):** `MANAGER`, `VP_FINANCE`, `CFO`.
  - **Logic:** Expenses `< $1,000` auto-approve. Expenses `[$1,000, $10,000)` require `MANAGER` approval. Expenses `>= $10,000` require `MANAGER` approval, which then auto-escalates to `VP_FINANCE` and finally to the `CFO` for a triple co-sign.
* **Healthcare (Surgical Procedure Requests):**
  - **Authority (`A`):** `ATTENDING_PHYSICIAN`, `CHIEF_OF_SURGERY`, `ANESTHESIOLOGIST`.
  - **Logic:** Routine clinical requests auto-approve. High-risk major procedures require the `ATTENDING_PHYSICIAN` to submit, the `CHIEF_OF_SURGERY` to authorize, and finally an `ANESTHESIOLOGIST` to sign off.

---

### 🔒 Recipe 2: `Reservable` (Scarce Capacity Holds & Evictions)

The `Reservable` recipe coordinates the temporary lock, countdown, confirmation, or release of a scarce, finite resource under high concurrent traffic, preventing double-allocations or capacity leakage.

#### What the Framework Orchestrates (Infrastructure)
* **Concurrency Protection:** Wraps pool checks in thread-safe, atomic transactions, shielding you from race conditions.
* **Automatic Expiry (TTL):** Automatically tracks hold durations. If a claim is not confirmed before its expiration timestamp (`now.isAfter(expiresAt)`), the process manager automatically evicts the hold and reclaims capacity.
* **Idempotency Safeguards:** Guarantees that confirming or releasing an already confirmed, expired, or released hold yields identical, repeatable success/failure blocks without duplicate state mutation.

#### What the Consumer Controls (Pure Business Logic)
The developer implements the pure `ReservableResource<ID, Q>` interface:
* `tryHold(ledger, holdId, actorId, quantity, now, expiresAt)`: Determines if there is sufficient uncommitted capacity to satisfy the lock. You define what "capacity" means (integers, float limits, vCPUs, physical space) and how active holds/reservations are mathematically aggregated.
* `tryConfirm(ledger, hold, reservationId, now)`: Asserts whether a hold is still valid and can be permanently converted into a reservation.
* `onRelease(hold)` & `onExpire(hold)`: Optional callback hooks allowing you to trigger notifications (e.g. sending a cart abandonment email) when holds expire.

#### Multi-Domain Adaptability Examples
* **E-Commerce (Warehouse Stock Allocation):**
  - **Quantity (`Q`):** `Integer` representing SKU unit counts.
  - **Logic:** When an item is added to a cart, it acquires a 10-minute inventory hold. If checkout succeeds, the hold is confirmed to permanently subtract inventory. If the timer expires, the units automatically return to the available stock pool.
* **SaaS / Cloud Computing (vCPU Instance Allocation):**
  - **Quantity (`Q`):** `Integer` representing requested virtual cores.
  - **Logic:** Before spinning up a virtual container, a 3-minute host hold is placed. Upon container initialization success, the cores are permanently reserved to that host node. If deployment fails, cores are instantly reclaimed.

---

## 📦 The 10 Domain Recipes Catalog

`expressj` includes 10 fully implemented, production-ready recipe processes:

| Recipe | Business Purpose | Standard State Transitions |
| :--- | :--- | :--- |
| **`Negotiable`** | Multi-party contract bargaining and terms bidding. | `INITIAL` ➔ `OFFERED` ➔ `COUNTERED` ➔ `ACCEPTED` / `WITHDRAWN` |
| **`Approvable`** | Hierarchical workflow validations and sequential reviews. | `PENDING` ➔ `APPROVED` / `REJECTED` / `ESCALATED` |
| **`Payable`** | Financial transactions, authorizations, and settlements. | `INITIAL` ➔ `AUTHORIZED` ➔ `CAPTURED` ➔ `REFUNDED` / `REVERSED` |
| **`Reservable`** | Temporary capacity locks, TTL evictions, and confirms. | `HELD` ➔ `CONFIRMED` / `RELEASED` / `EXPIRED` |
| **`Fulfillable`** | Logistics allocation, dispatch tracking, and delivery. | `INITIAL` ➔ `ALLOCATING` ➔ `PACKAGING` ➔ `DISPATCHED` ➔ `COMPLETED` |
| **`Ownable`** | Legal asset registration, authorization, and transfers. | `UNASSIGNED` ➔ `ASSIGNED` ➔ `TRANSFERRED` / `REVOKED` |
| **`Entitleable`**| Role permissions, security checks, and capability checks. | `REVOKED` ➔ `GRANTED` ➔ `CHECKED` |
| **`Meterable`** | High-performance telemetry aggregation and price rating. | `STARTED` ➔ `RECORDED` ➔ `RATED` (produces Rated Invoice) |
| **`Schedulable`**| Clock-driven cron jobs, rescheduling, and cancellations.| `SCHEDULED` ➔ `RESCHEDULED` ➔ `FIRED` / `CANCELLED` |
| **`Auditable`** | Chronological SHA-256 secure, tamper-evident log replays.| `INITIAL` ➔ `RECORDED` ➔ `REPLAYED` |

---

## 🚀 Rapid Prototyping: B2B E-Commerce Checkout Journey

Because all 10 recipes are completely decoupled, they do not share state or depend on one another. Instead, they are composed together **sequentially at the application boundary** using simple monad executions.

Our included sample application (`samples:ecommerce-checkout-journey`) showcases a complete **B2B Bulk Checkout & SLA Support Lifecycle** workflow:

```text
                       🚀 START CHECKOUT JOURNEY
                                    │
    1. [Negotiable]   ────► Bargain custom bulk Laptop price & counts.
                                    │
    2. [Approvable]   ────► Route 40% discount for Sales VP & CFO co-sign.
                                    │
    3. [Payable]      ────► Authorize $45,000 corporate purchase total.
                                    │
    4. [Reservable]   ────► Hold 50 laptops in West-Region warehouse pool.
                                    │
    5. [Fulfillable]  ────► Allocate, box, FedEx ship, and deliver laptops.
                                    │
    6. [Ownable]      ────► Register asset ownership to buyer-admin.
                                    │
    7. [Entitleable]  ────► Grant PREMIUM SLA warranty clearances.
                                    │
    8. [Meterable]    ────► Continuously log diagnostic support calls.
                                    │
    9. [Schedulable]  ────► Daily cron runs, rates support overages.
                                    │
   10. [Auditable]    ────► Replay chronological SHA-256 secure audit trails.
                                    │
                       🏁 JOURNEY COMPLETED SUCCESSFULLY!
```

---

## 🛠️ Getting Started & Verification Guide

### Prerequisites
* Java Development Kit (JDK) 21 or higher.
* Gradle (gradle-wrapper is included).

### 1. Build and Compile
Compile both the core library and the sample subproject:
```bash
./gradlew compileJava compileTestJava
```

### 2. Run the E-Commerce Checkout Simulation
Execute our interactive console-based B2B checkout simulation:
```bash
./gradlew :samples:ecommerce-checkout-journey:run
```
This runs the 10-step monadic workflow and prints beautifully structured, colorized terminal logs explaining exactly how the recipes collaborate in real-time.

### 3. Run the Test Suites
Run all unit and integration tests across the entire workspace (including both the core recipes and the sample verification suites):
```bash
./gradlew test
```
The test reports are automatically generated under `core/build/reports/tests/test/index.html` and `samples/ecommerce-checkout-journey/build/reports/tests/test/index.html`.

---

## 📂 Project Structure

```text
expressj/
├── core/                                 # The expressj Core Library
│   └── src/
│       ├── main/java/io/effects/         
│       │   ├── Either.java               # Type-safe Left/Right disjoint union
│       │   ├── IO.java                   # Lazy concurrent functional Monad
│       │   ├── ports/                    # StateRepository, EventPublisher, Telemetry ports
│       │   ├── adapters/                 # In-Memory & No-Op infrastructure implementations
│       │   └── recipes/                  # The 10 monadic Process Managers
│       └── test/java/io/effects/         # Comprehensive test coverage of all 10 recipes
│
└── samples/
    └── ecommerce-checkout-journey/       # Standalone B2B checkout demonstration subproject
        └── src/
            ├── main/java/.../ecommerce/  
            │   ├── EcommerceApp.java     # The 10-recipe Orchestration Engine & simulation main
            │   └── [recipe]/             # Reusable domain adapters (BulkOrderApproval, OrderPayment, etc.)
            └── test/java/.../ecommerce/  # End-to-end integration verification test suite
```

---

## 📝 License
This project is licensed under the Apache 2.0 License. See the `LICENSE` file for details.
