# Architecture & Design Decisions

## 1. Components and responsibilities

| Component | Responsibility | Not responsible for |
|---|---|---|
| **API service** (stateless) | Validate and accept jobs, persist them, expose status/history/stats, cancel | Executing jobs |
| **PostgreSQL** | Durable source of truth: job state, attempts, outbox, worker registry. Enforces uniqueness and atomic state changes | Message delivery |
| **Outbox + publisher** | Guarantee that every committed job eventually becomes an SQS message | Ordering, deduplication |
| **SQS primary queue** | At-least-once delivery, visibility timeouts, buffering, backlog metric for autoscaling | Job state, exactly-once |
| **SQS DLQ** | Quarantine for poison / exhausted messages | Deciding job status |
| **Workers** (N independent) | Claim, execute, record result, ack. Report heartbeats | Accepting HTTP jobs |
| **Redis** | Read cache for job status, short-lived heartbeat cache, rate-limit counters | Correctness (never required) |
| **CloudWatch / Micrometer** | Metrics, JSON logs, alarms, autoscaling signals | |

Redis is useful even though SQS is the queue: SQS cannot serve "what is the status of job X?" and polling clients
would otherwise hammer PostgreSQL. Redis absorbs those reads, plus rate-limit counters. If Redis is down, the system
falls back to PostgreSQL.

## 2. How accepted jobs are never lost (transactional outbox)

Writing to PostgreSQL and to SQS cannot be one atomic operation (dual-write problem). If the API committed the job
and then crashed before calling SQS, the job would sit in PENDING forever. Instead:

1. API, **one DB transaction**: `INSERT jobs (PENDING)` + `INSERT outbox_events`.
2. Publisher (Phase 2) polls `outbox_events WHERE published_at IS NULL AND next_publish_at <= now()` using
   `FOR UPDATE SKIP LOCKED` (safe with many publishers), sends to SQS, then sets `published_at` and moves the job
   `PENDING -> QUEUED`.
3. If the publisher crashes after sending but before marking published, the event is re-sent: **duplicate message**.
   This is acceptable because consumers are idempotent (section 4).

The outbox gives at-least-once publication; idempotent consumers turn that into effectively-once state changes.

## 3. Retries vs. SQS receive counts (two separate mechanisms)

- **Application retry count** (`jobs.retry_count`, `max_retries`) counts *failed executions* that the handler
  classified as retryable (HTTP 503, timeouts, rate limits). Permanent errors (invalid payload) go straight to FAILED.
- **SQS receive count** (`maxReceiveCount` on the redrive policy) counts *deliveries*, including ones where the worker
  crashed and never reported anything. It is the safety net, not the retry policy.

Design: on a retryable failure the worker, in **one DB transaction**, records the attempt, sets
`RETRYING + next_retry_at = now + backoff(jitter)`, and writes an outbox event scheduled for that time; then deletes
the original SQS message. The retry is therefore durable in PostgreSQL even if the worker dies right after.
Backoff is ~5s / 15s / 45s with jitter, configurable. When `retry_count == max_retries`, the job becomes
`DEAD_LETTERED` and a copy with the failure reason is sent to the DLQ. Crash/poison cases are caught by SQS redrive
(`maxReceiveCount`, e.g. 5), and a DLQ reconciler marks the corresponding job `DEAD_LETTERED` if it is not terminal.

## 4. Idempotency and duplicate delivery

Layers, from the edge inward:

1. **Client idempotency key** (`Idempotency-Key` header): partial unique index on `jobs.idempotency_key`. Same key +
   same request fingerprint returns the original job (200); same key + different request returns 409.
   Concurrent duplicates race on the unique index; the loser catches the violation and returns the winner's job.
2. **Atomic claim** (Phase 2): `UPDATE jobs SET status='RUNNING', worker_id=?, started_at=?, lease_expires_at=?,
   version=version+1 WHERE id=? AND (status IN ('QUEUED','RETRYING') OR (status='RUNNING' AND lease_expires_at < now()))`.
   One row updated = this worker owns the job. Zero rows = someone else owns it or it is terminal; ack and drop.
3. **Terminal-state check**: a duplicate for a COMPLETED/CANCELLED job is acked without executing.
4. **Idempotent result write**: `UPDATE ... WHERE id=? AND worker_id=? AND status='RUNNING'`. A worker that lost
   its lease cannot overwrite the new owner's result. `job_attempts` has `UNIQUE (job_id, attempt_number)`.

Limits: SQS Standard is at-least-once, so *exactly-once execution* of external side effects is impossible to guarantee.
For emails/webhooks we pass a deterministic downstream idempotency key (the `jobId`, identical across attempts)
so a cooperating receiver can deduplicate, and we document that non-cooperating receivers may see a duplicate.

## 5. Crash recovery

A worker holds a **lease** (`lease_expires_at`), renewed alongside SQS `ChangeMessageVisibility` while running.
If the worker dies, the message becomes visible again after the visibility timeout; the next worker's claim succeeds
because the lease expired. The previous attempt is recorded as abandoned and `retry_count` is incremented
consistently inside the claim transaction.

## 6. State machine

See `JobStatus`. Recovery from FAILED / DEAD_LETTERED is an explicit operation that goes through PENDING and writes a
new outbox event; it can never jump to COMPLETED/RUNNING directly. Cancellation is only allowed for PENDING, QUEUED,
RETRYING, enforced inside a conditional `UPDATE`, so it races safely with worker claims.

## 7. Database decisions

- `TIMESTAMPTZ` everywhere; the app writes UTC instants (`hibernate.jdbc.time_zone=UTC`).
- Partial indexes for hot, small slices: unpublished outbox rows, RETRYING-by-due-time, RUNNING-by-lease.
- `(status, created_at DESC)` and `(job_type, status, created_at DESC)` serve the list endpoint without sorting.
- Optimistic locking (`@Version`) for entity-level updates; conditional `UPDATE ... WHERE status IN (...)` for
  contended transitions (claim, cancel), which needs no retry loop.
- Flyway runs only in the API; workers start with `spring.flyway.enabled=false` to avoid migration races.
- `ddl-auto=validate`: Hibernate verifies the schema, Flyway owns it.

## 8. Key tradeoffs

| Choice | Why | Cost |
|---|---|---|
| SQS over Redis queue | Durable, managed, visibility timeout + DLQ built in | Per-request cost, 15 min max delay, no strict priority |
| Standard over FIFO | Higher throughput, simple scaling | No ordering, duplicates possible (handled by idempotency) |
| Outbox over direct publish | No lost jobs | Extra table, publish latency (poll interval), duplicates |
| PostgreSQL as truth, Redis as cache | Correctness never depends on cache | Cache invalidation logic |
| Horizontal worker scaling | Cheap Fargate tasks scale with backlog | DB connection count grows per worker |

`priority` is stored and exposed, but SQS Standard has no priority ordering. Real prioritisation needs separate
queues per priority tier (documented future work).

## 9. Phase status

| Phase | Status |
|---|---|
| 1 Foundation (schema, submit/get/list/cancel/attempts, validation, idempotent submit, outbox write) | Written, not yet compiled or run |
| 2 Queue and workers | Not started |
| 3 Reliability | Not started |
| 4 Redis, monitoring, auth, rate limiting, stats/workers APIs | Not started |
| 5 Testing and benchmarks | Not started |
| 6 AWS / Terraform | Not started (no AWS resources will be created without approval) |
| 7 Portfolio polish | Not started |
