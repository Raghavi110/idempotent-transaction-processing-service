# Idempotent Background Transaction Processing Service

A small Java 17 + Maven implementation of the FDE L2 assessment. It intentionally uses plain Java classes, an in-memory store and a JSON file input so the core behavior is easy to explain.

## 1. Requirements covered

- JSON input source behind a `TransactionSource` interface (replaceable by a queue/API/database adapter).
- Mandatory-field validation and invalid-record isolation.
- Transaction lifecycle: `RECEIVED`, `PROCESSING`, `PROCESSED`, `DUPLICATE`, `FAILED`, `RETRY_PENDING`.
- Idempotency keyed by `transactionId`; duplicate business transactions never apply their balance effect twice.
- Sequence-based out-of-order handling. Future sequence numbers wait until the expected sequence arrives.
- Simulated transient failure with bounded retry attempts.
- In-memory balance and transaction state store.
- Processing summary counts and operational logs.
- JUnit tests for duplicate, ordering, invalid input, retry and insufficient-funds behavior.

## 2. Design and assumptions

### Architecture

`JsonFileTransactionSource` -> `TransactionProcessor` -> `TransactionValidator` + in-memory transaction/balance stores.

The input source is separate from business processing. A future Kafka, SQS, REST or database adapter can implement `TransactionSource` without changing transaction rules.

### Idempotency

`transactionId` is the business idempotency key. The processor registers the transaction before applying its effect. If the same transaction ID appears again—even with a different request ID—it is reported as `DUPLICATE` and its balance effect is not repeated.

### Ordering strategy

For this assessment, `sequenceNumber` is treated as one ordered input stream starting at 1. A future sequence is stored as `RETRY_PENDING`. When the missing sequence arrives, it is processed and the waiting sequence is drained. This matches the supplied sample where sequence 2 arrives before sequence 1 and later sequence values continue across accounts.

A production system should confirm whether sequence is global or per account. If it is per account, maintain `nextExpectedSequence` and a waiting queue per account instead.

Invalid records are isolated. If an invalid record occupies the currently expected sequence, that sequence slot is consumed so malformed input does not block the remainder of this demo stream. This is a deliberate assessment simplification; production policy may instead quarantine the stream and require operator repair.

### Retry and failure handling

`failFirstAttempt=true` simulates a transient downstream error. The processor marks the record `RETRY_PENDING`, retries it, and succeeds on the next attempt. Maximum attempts are configurable in the processor constructor (sample uses 3). A failed debit due to insufficient funds is treated as a non-transient business failure and is not retried.

### Consistency and concurrency

All processor entry points are synchronized. In this single-process demo, balance update and transaction status update happen in one critical section. This is not a distributed transaction and does not survive process restart.

### Observability

The application logs accepted processing, duplicates, waiting records, retries and failures. It prints per-record outcomes, account balances and a status summary. Logs avoid printing amounts or other unnecessary customer details.

## 3. Run locally

Requirements: JDK 17+ and Maven 3.8+.

```bash
mvn clean test
mvn exec:java
```

Use another JSON input file:

```bash
mvn exec:java -Dexec.args="sample-data/transactions.json"
```

## 4. Sample input behavior

The sample demonstrates:
1. `TXN-2002` sequence 2 arrives first and waits.
2. `TXN-2001` sequence 1 arrives; it is credited, then sequence 2 is debited.
3. `TXN-2002` arrives again with another request ID and is marked duplicate.
4. `TXN-2003` is credited.
5. `TXN-2004` is rejected because its amount is negative; the batch continues.
6. `TXN-2005` simulates one transient failure, retries and succeeds.

Expected final balances:
- `ACC-201`: 85.00 (100 credit - 40 debit + 25 credit)
- `ACC-202`: 0.00 (invalid debit rejected)
- `ACC-203`: 500.00 (one simulated failure, then successful credit)

## 5. Test strategy

Run `mvn test`. The unit tests verify:
- Out-of-order events wait and are drained when the missing sequence arrives.
- Duplicate transaction IDs do not create a second balance update.
- Invalid transactions are rejected without stopping the batch.
- Transient failures retry and apply the balance effect once.
- Insufficient funds fail safely without changing the balance.

## 6. Production hardening (not included in this time-boxed implementation)

1. Persist transaction/idempotency state and ledger entries in a relational database.
2. Enforce a unique database constraint on `transaction_id` and update ledger + transaction state in one database transaction.
3. Use a transactional outbox for downstream side effects.
4. Use durable queue acknowledgements, visibility timeouts and a dead-letter queue.
5. Make retry policy configurable with exponential backoff and jitter; distinguish transient from permanent errors.
6. Define ordering scope (global, account, or aggregate) with the business owner and partition the queue accordingly.
7. Add metrics (processed, duplicate, pending age, retry count, failure rate), correlation IDs and health checks.
8. Add authentication, authorization, input-size limits and secret management.
9. Add concurrency/integration tests against the chosen database and queue.

## 7. Explaining the solution to the team

Start with the flow: validate -> check idempotency -> check sequence -> process -> update balance and state -> retry/report.

The important trade-off is that this is an intentionally small, single-process reference implementation. It demonstrates the core rules, but production-grade exactly-once effects require durable storage, atomic database constraints/transactions, and idempotent downstream integrations.

## 8. AI-assisted development note

AI assistance was used to accelerate project scaffolding and test-case coverage. The implementation should be reviewed and validated by running the Maven test suite and walking through the sample input. The candidate should be able to explain the idempotency key, ordering assumption, retry boundary, in-memory limitations and production-hardening steps.
