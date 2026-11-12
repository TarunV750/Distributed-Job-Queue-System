-- All timestamps are TIMESTAMPTZ and written as UTC instants by the application.

CREATE TABLE jobs (
    id               UUID PRIMARY KEY,
    job_type         VARCHAR(50)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    payload          JSONB        NOT NULL,
    result           JSONB,
    error_message    TEXT,
    retry_count      INTEGER      NOT NULL DEFAULT 0,
    max_retries      INTEGER      NOT NULL DEFAULT 3,
    priority         INTEGER      NOT NULL DEFAULT 5,
    idempotency_key  VARCHAR(255),
    request_hash     VARCHAR(64),
    created_at       TIMESTAMPTZ  NOT NULL,
    queued_at        TIMESTAMPTZ,
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    next_retry_at    TIMESTAMPTZ,
    worker_id        VARCHAR(100),
    lease_expires_at TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_jobs_status CHECK (status IN
        ('PENDING','QUEUED','RUNNING','RETRYING','COMPLETED','FAILED','DEAD_LETTERED','CANCELLED')),
    CONSTRAINT chk_jobs_retry_count CHECK (retry_count >= 0),
    CONSTRAINT chk_jobs_max_retries CHECK (max_retries >= 0),
    CONSTRAINT chk_jobs_priority CHECK (priority BETWEEN 1 AND 10)
);

-- Idempotency: a key identifies at most one job. Partial so NULL keys are unconstrained.
CREATE UNIQUE INDEX uq_jobs_idempotency_key ON jobs (idempotency_key) WHERE idempotency_key IS NOT NULL;

-- GET /jobs?status=..  (newest first)
CREATE INDEX idx_jobs_status_created ON jobs (status, created_at DESC);
-- GET /jobs?jobType=..&status=..
CREATE INDEX idx_jobs_type_status_created ON jobs (job_type, status, created_at DESC);
-- Plain listing, newest first
CREATE INDEX idx_jobs_created ON jobs (created_at DESC);
-- Retry/recovery sweepers only look at a tiny slice of the table, so keep the indexes partial.
CREATE INDEX idx_jobs_retrying_due ON jobs (next_retry_at) WHERE status = 'RETRYING';
CREATE INDEX idx_jobs_running_lease ON jobs (lease_expires_at) WHERE status = 'RUNNING';
CREATE INDEX idx_jobs_running_worker ON jobs (worker_id) WHERE status = 'RUNNING';

CREATE TABLE job_attempts (
    id                    BIGSERIAL PRIMARY KEY,
    job_id                UUID         NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    worker_id             VARCHAR(100) NOT NULL,
    attempt_number        INTEGER      NOT NULL,
    started_at            TIMESTAMPTZ  NOT NULL,
    finished_at           TIMESTAMPTZ,
    status                VARCHAR(20)  NOT NULL,
    error_message         TEXT,
    execution_duration_ms BIGINT,
    -- Makes attempt recording idempotent: the same attempt cannot be inserted twice.
    CONSTRAINT uq_job_attempt UNIQUE (job_id, attempt_number)
);

CREATE TABLE outbox_events (
    id               UUID PRIMARY KEY,
    aggregate_id     UUID         NOT NULL,
    event_type       VARCHAR(50)  NOT NULL,
    payload          JSONB        NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    published_at     TIMESTAMPTZ,
    publish_attempts INTEGER      NOT NULL DEFAULT 0,
    next_publish_at  TIMESTAMPTZ  NOT NULL
);

-- The publisher polls only unpublished rows; the partial index stays tiny.
CREATE INDEX idx_outbox_unpublished ON outbox_events (next_publish_at) WHERE published_at IS NULL;

CREATE TABLE worker_heartbeats (
    worker_id      VARCHAR(100) PRIMARY KEY,
    hostname       VARCHAR(255),
    status         VARCHAR(20)  NOT NULL,
    last_heartbeat TIMESTAMPTZ  NOT NULL,
    jobs_processed BIGINT       NOT NULL DEFAULT 0,
    jobs_failed    BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_worker_heartbeats_last ON worker_heartbeats (last_heartbeat DESC);
