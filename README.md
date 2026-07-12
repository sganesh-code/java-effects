# 🚀 expressj: Collaborative Business Recipes for Rapid Prototyping

[![CI](https://github.com/sganesh-code/java-effects/actions/workflows/ci.yml/badge.svg)](https://github.com/sganesh-code/java-effects/actions/workflows/ci.yml)

`expressj` is a lightweight, high-performance, and language-native Java library providing core business logic as reusable, type-safe **"Recipes"** (collaboration protocols). 

By separating high-level business rules from low-level infrastructure, `expressj` enables teams to rapidly identify, model, and deploy complex business workflows completely isolated from the details of databases, message brokers, and execution environments.

---

## 🏛️ Core Philosophy: Focused Business Prototyping

In traditional software development, business logic is frequently coupled with database transactions, web endpoints, and threading code. This introduces significant complexity and makes systems difficult to adapt. `expressj` completely eliminates three classic enterprise development pitfalls:

1. **Infrastructure Leakage:** In traditional architectures, database connections, framework annotations (such as `@Transactional`), SQL queries, and networking libraries creep directly into core business rules. This muddies domain boundaries and makes the business logic fragile. `expressj` keeps business rules 100% pure, synchronous, and side-effect-free.
2. **Abstractions & Maintenance Barriers:** Software teams often reinvent the wheel when designing coordination logic (like managing reservations, approval escalation paths, or financial transactions). This results in custom, tightly coupled "spaghetti" code that is extremely hard to extend. `expressj` provides these core collaborative workflows as pre-built, mathematically sound, reusable business recipes.
3. **Environment Portability Barriers:** Porting business logic to work with new environments—such as migrating from a lightweight local test runner to a production Spring Boot microservice, or changing messaging channels from in-memory mocks to **Apache Kafka** or **Redis Streams**—typically requires a massive rewrite. With `expressj`'s Port and Adapter design, the core business rules remain completely untouched while you swap infrastructure adapters seamlessly.

---

## 🏛️ Hexagonal Portability: Separating Domain from Infrastructure

`expressj` is built natively on Hexagonal (Ports & Adapters) principles. The core business rules (Recipes) have zero knowledge of how data is persisted or how messages are physically routed:

```text
       ┌────────────────────────────────────────────────────────┐
       │             EXTERNAL ADAPTERS (Infrastructure)         │
       │                                                        │
       │  • Relational DBs (PostgreSQL) / Key-Value (Redis)     │
       │  • Messaging Brokers (Apache Kafka / Redis Streams)    │
       │  • Local Test Runners (In-Memory Stubs)                │
       └──────────────────────────┬─────────────────────────────┘
                                  │ (Plugs into Ports)
                                  ▼
       ┌────────────────────────────────────────────────────────┐
       │             CORE BUSINESS RECIPES (Pure Domain)        │
       │                                                        │
       │  • Declarative collaboration state transitions         │
       │  • Unified business policies (no getters/setters)      │
       │  • Independent of frameworks, SQL, or networks         │
       └────────────────────────────────────────────────────────┘
```

By defining clean, abstract **Ports** (such as `EventPublisher`, `EventSubscriber`, and `StateRepository`), `expressj` lets you swap infrastructure **Adapters** based on individual application requirements. 

For instance, during local development and rapid prototyping, you can run your entire business workflow using **In-Memory adapters** to get sub-millisecond feedback. When deploying to production, you can configure the exact same domain code to run on a high-throughput, enterprise-scale **Apache Kafka** cluster or a persistent **Redis Streams** pipeline simply by changing your configuration.

---

## ⚡ Rapid Development: Model High-Level, Delegate Low-Level

`expressj` accelerates software delivery by allowing developers to focus on identifying, mapping, and modeling high-level business concepts, while delegating low-level state transitions and system invariants to the library's recipes:

* **High-Level Modeling (Your Focus):** You spend your time defining corporate pricing policies, carrier dispatch rules, executive review requirements, and warranty SLA criteria.
* **Low-Level Execution (Delegated to Recipes):** The recipes automatically handle concurrency locks, chronological state ledger auditing, expiration timelines (TTLs), transactional guarantees, and cross-departmental message routing loops in a safe, non-blocking environment.

---

## 📦 Supported Business Recipes Catalog

`expressj` supports a rich catalog of collaborative business recipes representing standard workflows across commercial applications:

| Recipe Name | Core Business Algebra & Messages | Real-World Business Scenario / Example |
| :--- | :--- | :--- |
| **1. Negotiable** | `initiate`, `makeOffer`, `makeCounter`, `accept` | **B2B Bulk Pricing Contract:** A corporate buyer and sales system negotiate volume-based discount pricing terms until a mutually accepted contract is settled. |
| **2. Approvable** | `submit`, `approve`, `reject`, `escalate` | **Corporate Expense Approval:** Submitting a high-value purchase discount request that requires sequential approvals and automatic escalations (e.g., Sales VP $\rightarrow$ CFO). |
| **3. Payable** | `authorize`, `capture`, `reverse`, `refund` | **E-Commerce Billing Settlement:** Pre-authorizing customer credit on their corporate account and capturing/settling the funds once the order has been dispatched. |
| **4. Reservable** | `hold`, `confirm`, `release`, `expire` | **Warehouse Stock Reservation:** Securing temporary holds on physical stock during checkout. Holds automatically expire and return to inventory if checkout isn't completed. |
| **5. Fulfillable** | `initiate`, `allocate`, `package`, `dispatch`, `complete` | **Logistics shipping packages:** Allocating items at warehouse packing stations, boxing and labeling them, and tracking shipping carriers through final delivery. |
| **6. Ownable** | `register`, `assign`, `transfer` | **Corporate Asset Assignment:** Logging a physical device's serial number (e.g. laptop) and assigning or transferring its ownership to specific corporate employee accounts. |
| **7. Entitleable** | `grant`, `check`, `revoke` | **Warranty Support SLA Coverage:** Assigning premium support SLA access levels to customer assets and verifying maintenance session authorization boundaries. |
| **8. Observable** | `register`, `subscribe`, `unsubscribe`, `publish` | **Cross-Departmental Messaging Broker:** Allowing different corporate systems (Warehouse, Shipping, Billing) to subscribe to events and react asynchronously as a choreographed pipeline. |
| **9. Schedulable** | `schedule`, `reschedule`, `trigger`, `cancel` | **Deferred Task Execution:** Scheduling premium member renewal invoicing, automated batch report generation, or delayed subscription cancellations. |
| **10. Meterable** | `meterUsage`, `resetUsage`, `quotaCheck` | **Pay-As-You-Go Billing:** Tracking cloud resource consumption, API call volumes, or data transfer bytes to dynamically compute usage-based invoices. |
| **11. Auditable** | `execute`, `audit`, `queryHistory` | **Chronological Compliance Ledger:** Writing an immutable, tamper-evident chronological ledger of critical user actions or balance adjustments for security compliance. |
| **12. Routable** | `route`, `reroute`, `reject` | **Dynamic Support Dispatch:** Directing incoming customer support cases or logistics package deliveries to appropriate regional handlers or specialized support tiers based on capacity policies. |
| **13. Reconciliable** | `match`, `flagDiscrepancy`, `resolve` | **Bank Ledger Cash Match:** Matching internal corporate bookkeeping transaction streams against external monthly bank statement files to discover and resolve cash discrepancies. |
| **14. Retryable** | `execute`, `recordFailure`, `retryBackoff`, `abandon` | **Payment Gateway Resiliency:** Wrapping transient third-party card authorizations with adaptive exponential backoffs, scheduling retries, and recording final abandonment on permanent errors. |
| **15. Claimable** | `file`, `review`, `accept`, `deny`, `dispute` | **Insurance Warranty Auditing:** Managing claimant warranty claims, coordinating medical/auditor review transitions, and validating re-opened claimant disputes. |
| **16. Prioritizable** | `sequence`, `reprioritize`, `defer`, `expedite` | **Emergency Queue Triage:** Directing high-priority hospital triage or SaaS SLA tickets, dynamically reprioritizing cases, deferring non-urgent work, and expediting critical outages. |
| **17. Compensable** | `runStep`, `triggerRollback`, `markCompensated`, `markCompensationFailure` | **Distributed Checkout Saga:** Coordinating hotel, flight, and rental car bookings. If booking flight fails, previous hotel reservations are automatically cancelled/refunded in reverse LIFO order. |
| **18. Escalatable** | `file`, `triggerSLAWarning`, `escalate`, `deescalate`, `reassign` | **SLA Incident Management:** Raising warning flags as service desk deadlines approach, automatically escalating unhandled tickets to Tier-3 support, and reassigning engineers. |
| **19. Throttlable** | `consume`, `throttle`, `refill` | **API Client Rate Limiting:** Enforcing fair-use Token Bucket backpressures against bursty API consumer requests, dynamically refilling quotas over elapsed milliseconds. |

---

## 🔄 Compare & Contrast: Traditional vs. expressj

| Architectural Aspect | Traditional Enterprise Architecture (Spring / Hibernate / JPA Services) | expressj Architectural Design (Pure Collaborative Recipes) |
| :--- | :--- | :--- |
| **Domain Model** | **Anemic Models:** Driven by database schemas with passive data-holding objects (getters/setters). Pure business logic is scattered across procedural transaction service classes. | **Rich Behavioral Models:** Focuses strictly on object-collaboration and business behaviors. Pure domain rules are encapsulated inside getter-free, non-anemic classes. |
| **Infrastructure Coupling** | **High Coupling:** Business logic is directly tied to database operations, active connection pools, framework annotations, and third-party network libraries. | **Complete Separation:** Business logic is completely isolated. Physical storage systems, brokers, and networks are plugged in as external adapters via abstract ports. |
| **Testing Feedback Loop** | **Slow and Brittle:** Verifying basic discount calculations requires spinning up heavy frameworks, mock configurations (Mockito), or Testcontainers databases. | **Instantaneous:** 100% of your business rules and state transitions can be verified in milliseconds using standard, zero-dependency unit tests. |
| **Concurrency & State Safety** | **Complex & Error-Prone:** Developers must manually manage thread locks, pessimistic/optimistic db locking, or distributed mutexes, which frequently causes deadlocks. | **Guaranteed by Design:** Recipes mathematically guarantee thread safety and chronological state transitions inside the shell, protecting the domain core. |
| **Infrastructure Portability** | **Locked-In:** Migrating from a relational DB (PostgreSQL) to a NoSQL DB (DynamoDB), or switching from RabbitMQ to Apache Kafka, requires rewriting the entire application. | **Plug-And-Play:** The exact same business logic runs untouched; changing infrastructure only requires writing or selecting a small, isolated external adapter. |

---

## 📦 Dependency Coordinates

To start using `expressj` in your own projects, add the core module dependency:

### Gradle (Kotlin DSL)
```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.effects:core:0.1.0")
}
```

### Maven
```xml
<dependency>
    <groupId>io.effects</groupId>
    <artifactId>core</artifactId>
    <version>0.1.0</version>
</dependency>
```

---

## ⚡ Quick Start: Your First Business Recipe

Let's see how simple it is to model a corporate **Approval Workflow** (such as approving discount proposals or purchase requisitions) using `expressj`'s standard **Approvable** recipe, completely isolated from databases or network brokers.

### 1. Define Your Getter-Free Business Rules

Create a record that implements `ApprovableRequest`. Note that it has **zero passive getters/setters** and encapsulates all corporate pricing/discount policies within pure, testable domain behaviors:

```java
import io.effects.recipes.approvable.*;
import io.effects.recipes.approvable.models.*;
import io.effects.core.Either;
import java.time.Instant;

public record DiscountProposal(double discountPercentage) implements ApprovableRequest<String, String, String> {

    @Override
    public InitialAssessment<String> evaluateInitialSubmission(Instant now) {
        if (discountPercentage < 10.0) {
            return new InitialAssessment<>(Status.APPROVED, null); // Auto-approved!
        } else if (discountPercentage < 25.0) {
            return new InitialAssessment<>(Status.PENDING, "MANAGER"); // Requires Manager
        } else {
            return new InitialAssessment<>(Status.PENDING, "VP"); // Requires VP
        }
    }

    @Override
    public Either<String, NextStep<String>> evaluateDecision(
            ApprovalRecord<String, String, String> record,
            String approverId,
            String approverRole,
            DecisionType decisionType,
            String comment,
            Instant now
    ) {
        if (record.isTerminal()) {
            return Either.left("Proposal is already in a terminal state");
        }

        String required = record.requiredAuthority();
        if (required != null && !required.equalsIgnoreCase(approverRole)) {
            return Either.left("Insufficient authority! Expected role " + required + " but got " + approverRole);
        }

        if (decisionType == DecisionType.REJECT) {
            return Either.right(new NextStep<>(Status.REJECTED, null));
        }

        return Either.right(new NextStep<>(Status.APPROVED, null));
    }
}
```

### 2. Execute and Test the Flow

Now, run the workflow using the `ApprovalProcess` engine. You can instantiate it with standard, zero-dependency **In-Memory** adapters for rapid local prototyping:

```java
import io.effects.recipes.approvable.*;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.NoOpTelemetryPort;
import java.time.Instant;

public class Main {
    public static void main(String[] args) {
        // Setup rapid in-memory local adapters (ports and adapters design)
        var repo = new InMemoryStateRepository<String, ApprovalRecord<String, String, String>>();
        var publisher = new InMemoryEventPublisher<ApprovalEvent<String, String>>();
        var process = new ApprovalProcess<>(repo, publisher, new NoOpTelemetryPort());

        String proposalId = "prop-901";
        DiscountProposal proposal = new DiscountProposal(15.0); // 15% discount
        Instant now = Instant.now();

        // 1. Register and submit the proposal
        process.register(proposalId, proposal).unsafeRunSync();
        var submitResult = process.submit(proposalId, "sales-agent-A", "Special contract discount", now).unsafeRunSync();
        
        ApprovalRecord<String, String, String> record = submitResult.getRight();
        System.out.println("Status: " + record.status()); // PENDING
        System.out.println("Required Role: " + record.requiredAuthority()); // MANAGER

        // 2. Approve with sufficient authority (MANAGER role)
        var approveResult = process.approve(proposalId, "mgr-B", "MANAGER", "Within budget limits", now.plusSeconds(5)).unsafeRunSync();
        
        ApprovalRecord<String, String, String> approvedRecord = approveResult.getRight();
        System.out.println("Final Status: " + approvedRecord.status()); // APPROVED
        System.out.println("Is Terminal: " + approvedRecord.isTerminal()); // true
    }
}
```
