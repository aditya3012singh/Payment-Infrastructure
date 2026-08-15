# Payment Systems High Level Design (HLD)

This document visualizes the 4 major payment architectures used in the tech industry today, ranging from simple MVPs to Enterprise-grade distributed systems.

---

## 1. The Redirect Flow (Frontend Checkout / Webhooks)
**Use Case:** Small e-commerce, MVPs, Shopify stores.
**Pros:** Easy to build, PCI compliance handled by Stripe.
**Cons:** Slow UX, no control over the checkout experience, redirect drops.

```mermaid
sequenceDiagram
    participant User
    participant Frontend
    participant Backend
    participant Stripe

    User->>Frontend: Clicks "Pay"
    Frontend->>Stripe: Redirects to checkout.stripe.com
    User->>Stripe: Enters Card & OTP
    Stripe->>Frontend: Redirects back to Merchant (Success URL)
    Stripe-->>Backend: [Async Webhook] POST /stripe/webhook "Payment Succeeded"
    Backend->>Backend: Verifies Signature
    Backend->>Backend: Updates DB to COMPLETED
```

---

## 2. Direct Server-to-Server (Synchronous API)
**Use Case:** Startups wanting a custom UX, simple internal apps.
**Pros:** Clean UX, user never leaves the site.
**Cons:** Blocks HTTP threads, user stares at loading screen, fails completely if Stripe is down.

```mermaid
sequenceDiagram
    participant User
    participant Frontend
    participant Backend
    participant Database
    participant Stripe

    User->>Frontend: Enters Card details securely
    Frontend->>Stripe: Tokenizes Card (returns tok_123)
    Frontend->>Backend: POST /pay (tok_123)
    Note over Backend: Thread blocked waiting for Stripe!
    Backend->>Stripe: Charge tok_123 for $100 (Synchronous)
    Stripe-->>Backend: HTTP 200 OK
    Backend->>Database: Save Payment as COMPLETED
    Backend-->>Frontend: HTTP 200 Success
    Frontend-->>User: Shows Green Checkmark ✅
```

---

## 3. The Asynchronous Event-Driven Architecture (What we built!)
**Use Case:** Wallets, Subscriptions, Payouts, Medium-Large Enterprises.
**Pros:** Lightning-fast UX, survives database crashes, handles network outages with Kafka retries.
**Cons:** Requires Kafka setup, Eventual Consistency, not suited for OTPs.

```mermaid
sequenceDiagram
    participant User
    participant Frontend
    participant Payment API
    participant PostgreSQL
    participant Kafka
    participant Consumer
    participant Stripe

    User->>Frontend: Clicks "Pay"
    Frontend->>Payment API: POST /pay
    Payment API->>PostgreSQL: Verify Idempotency (Redis)
    Payment API->>PostgreSQL: Transactional Write (Payment, Ledger, Outbox)
    Payment API-->>Frontend: HTTP 200 "Processing" (Instantly!)
    Frontend-->>User: ✅ Request Received
    
    loop Every 5 Seconds (Outbox Poller)
        PostgreSQL-->>Kafka: Push Outbox Event to topic
    end
    
    Kafka-->>Consumer: Reads Event
    Consumer->>Stripe: Charge Card (Async)
    
    alt If Stripe is down
        Stripe-->>Consumer: HTTP 503 Error
        Consumer->>Kafka: DLQ / Exponential Backoff Retry Topic
    else If Stripe succeeds
        Stripe-->>Consumer: HTTP 200 OK
        Consumer->>PostgreSQL: Update Status to COMPLETED
    end
```

---

## 4. The State Machine / Saga Orchestration (Netflix / Uber scale)
**Use Case:** Complex e-commerce flows (Flight Booking, Multi-merchant carts).
**Pros:** Perfect rollback capabilities (Sagas), impossible to lose state, orchestrates 10+ microservices.
**Cons:** Extremely complex, requires tools like Temporal, AWS Step Functions, or Netflix Conductor.

```mermaid
sequenceDiagram
    participant User
    participant Frontend
    participant Orchestrator (Temporal)
    participant Inventory MS
    participant Payment MS
    participant Shipping MS

    User->>Frontend: Clicks "Buy TV"
    Frontend->>Orchestrator (Temporal): Start Checkout Workflow

    Orchestrator (Temporal)->>Inventory MS: [Step 1] Reserve TV
    Inventory MS-->>Orchestrator (Temporal): Success (Reserved)

    Orchestrator (Temporal)->>Payment MS: [Step 2] Charge Card
    Payment MS-->>Orchestrator (Temporal): Success (Charged $500)

    Orchestrator (Temporal)->>Shipping MS: [Step 3] Create Shipping Label
    Shipping MS-->>Orchestrator (Temporal): FAILED (Invalid Zip Code!)

    Note over Orchestrator (Temporal): 🚨 Initiating Compensating Transactions (Rollback)

    Orchestrator (Temporal)->>Payment MS: [Rollback Step 2] Refund $500
    Payment MS-->>Orchestrator (Temporal): Success (Refunded)

    Orchestrator (Temporal)->>Inventory MS: [Rollback Step 1] Return TV to stock
    Inventory MS-->>Orchestrator (Temporal): Success (Restocked)

    Orchestrator (Temporal)-->>Frontend: Checkout Failed, Money Refunded
    Frontend-->>User: Error: Cannot ship to your location.
```
