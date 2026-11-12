package com.jobqueue.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A message that must reach SQS. Written in the same transaction as the state change that caused it. */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    public static final String JOB_SUBMITTED = "JOB_SUBMITTED";

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    @Column(name = "next_publish_at", nullable = false)
    private Instant nextPublishAt;

    protected OutboxEvent() {
        // JPA
    }

    public static OutboxEvent jobSubmitted(Job job, Instant now) {
        OutboxEvent e = new OutboxEvent();
        e.id = UUID.randomUUID();
        e.aggregateId = job.getId();
        e.eventType = JOB_SUBMITTED;
        e.payload = Map.of(
                "jobId", job.getId().toString(),
                "jobType", job.getJobType().name(),
                "priority", job.getPriority());
        e.createdAt = now;
        e.nextPublishAt = now;
        return e;
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public Map<String, Object> getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getPublishAttempts() { return publishAttempts; }
    public Instant getNextPublishAt() { return nextPublishAt; }
}
