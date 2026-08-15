# Enterprise Payflow Architecture

An enterprise-grade, asynchronous, event-driven payment processing engine built with Java, Spring Boot, PostgreSQL, Redis, and Kafka.

This repository demonstrates how massive tech companies (Uber, Stripe, Netflix) architect their core money-moving systems to guarantee financial integrity, extreme resilience, and lightning-fast user experiences.

## Core Features & Concepts Implemented

1. **Idempotency (Redis)**
   - Prevents double-charging if a user clicks the "Pay" button twice or if a network partition causes a duplicate request.
   - Uses atomic `SETNX` commands in Redis with a 24-hour expiration lock.

2. **Double-Entry Accounting Ledger (PostgreSQL)**
   - Money is never "created" or "destroyed", it is only moved between accounts.
   - Every payment generates a balanced `LedgerTransaction` with paired `LedgerEntry` debits and credits.
   - Uses `PESSIMISTIC_WRITE` locks to prevent race conditions when 1,000 users pay the same merchant simultaneously.

3. **The Transactional Outbox Pattern**
   - Guarantees 100% reliable event delivery.
   - Instead of sending messages to Kafka mid-transaction (which can lead to "ghost messages" if the database rolls back), events are written to an `outbox_events` table inside the exact same atomic database transaction as the payment.
   - A background poller reads the outbox and publishes to Kafka.

4. **Asynchronous Processing (Kafka)**
   - Decouples the frontend UX from slow external 3rd-party banking APIs.
   - The user gets an instant `HTTP 200 Processing` response in ~10 milliseconds.
   - A Kafka consumer takes the event and talks to the external gateway in the background.

5. **Idempotent Consumers & Dead Letter Queues**
   - If the external banking API goes down, the Kafka consumer automatically retries with exponential backoff (`@RetryableTopic`).
   - If it fails 4 times, it routes to a Dead Letter Queue (`@DltHandler`) to mark the payment as `FAILED`.
   - Consumer maintains its own Redis idempotency to prevent processing the same Kafka message twice (at-least-once delivery protection).

6. **The Reconciliation Engine**
   - A scheduled batch job (`@Scheduled`) that runs daily to compare our local PostgreSQL Database's "Completed" payments against the external Gateway's "Settlement Report".
   - Flags discrepancies (missing money, falsely completed payments) for human review.

## High Level Design
Please check out the [High Level Design Document](high-level-design/README.md) for Mermaid.js sequence diagrams detailing how this architecture compares against simple Webhook flows and complex Saga Orchestrators.

## Tech Stack
- **Java 17** & **Spring Boot 3.x**
- **PostgreSQL** (Relational Data & Ledger)
- **Redis** (Idempotency Locks)
- **Redpanda / Kafka** (Event Streaming)
- **Docker Compose** (Infrastructure)

## How to Run
1. Start the infrastructure (Postgres, Redis, Kafka):
   ```bash
   docker-compose up -d
   ```
2. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```
3. Send a test payment (change `idempotencyKey` on subsequent requests!):
   ```bash
   curl -X POST http://localhost:8080/api/v1/payments \
   -H "Content-Type: application/json" \
   -d '{"idempotencyKey": "order-12345", "amount": 250.00, "currency": "USD"}'
   ```
