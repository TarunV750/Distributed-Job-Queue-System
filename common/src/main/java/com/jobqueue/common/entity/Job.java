package com.jobqueue.common.entity;

import com.jobqueue.common.exception.InvalidStateTransitionException;
import com.jobqueue.common.model.JobStatus;
import com.jobqueue.common.model.JobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 50)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(nullable = false)
    private int priority;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "queued_at")
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "worker_id")
    private String workerId;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    /** Wrapper type on purpose: null marks a new entity for Spring Data, and drives optimistic locking. */
    @Version
    private Long version;

    protected Job() {
        // JPA
    }

    public static Job create(UUID id, JobType type, Map<String, Object> payload, int maxRetries, int priority,
                             String idempotencyKey, String requestHash, Instant now) {
        Job job = new Job();
        job.id = id;
        job.jobType = type;
        job.status = JobStatus.PENDING;
        job.payload = payload;
        job.maxRetries = maxRetries;
        job.priority = priority;
        job.retryCount = 0;
        job.idempotencyKey = idempotencyKey;
        job.requestHash = requestHash;
        job.createdAt = now;
        return job;
    }

    /** Applies a status change only if the transition table allows it. */
    public void transitionTo(JobStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidStateTransitionException(status, next);
        }
        this.status = next;
    }

    public UUID getId() { return id; }
    public JobType getJobType() { return jobType; }
    public JobStatus getStatus() { return status; }
    public Map<String, Object> getPayload() { return payload; }
    public Map<String, Object> getResult() { return result; }
    public String getErrorMessage() { return errorMessage; }
    public int getRetryCount() { return retryCount; }
    public int getMaxRetries() { return maxRetries; }
    public int getPriority() { return priority; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getQueuedAt() { return queuedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public String getWorkerId() { return workerId; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public Long getVersion() { return version; }
}
