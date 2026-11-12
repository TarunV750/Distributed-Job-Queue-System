package com.jobqueue.common.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Job lifecycle with an explicit transition table.
 *
 * <pre>
 * PENDING -> QUEUED -> RUNNING -> COMPLETED
 *                         |-> RETRYING -> RUNNING ...
 *                         |-> FAILED          (permanent error, no retry)
 *                         |-> DEAD_LETTERED   (retries / deliveries exhausted)
 * FAILED / DEAD_LETTERED -> PENDING           (explicit retry / replay only)
 * PENDING / QUEUED / RETRYING -> CANCELLED
 * </pre>
 *
 * A worker taking over a RUNNING job whose lease expired is a re-claim (same status, new worker),
 * not a status transition.
 */
public enum JobStatus {
    PENDING,
    QUEUED,
    RUNNING,
    RETRYING,
    COMPLETED,
    FAILED,
    DEAD_LETTERED,
    CANCELLED;

    private static final Map<JobStatus, Set<JobStatus>> TRANSITIONS = new EnumMap<>(JobStatus.class);

    static {
        TRANSITIONS.put(PENDING, EnumSet.of(QUEUED, CANCELLED));
        TRANSITIONS.put(QUEUED, EnumSet.of(RUNNING, CANCELLED, DEAD_LETTERED));
        TRANSITIONS.put(RUNNING, EnumSet.of(COMPLETED, RETRYING, FAILED, DEAD_LETTERED));
        TRANSITIONS.put(RETRYING, EnumSet.of(RUNNING, CANCELLED, DEAD_LETTERED));
        TRANSITIONS.put(COMPLETED, EnumSet.noneOf(JobStatus.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(JobStatus.class));
        // Recovery is explicit: goes back through PENDING so a fresh outbox event is written.
        TRANSITIONS.put(FAILED, EnumSet.of(PENDING));
        TRANSITIONS.put(DEAD_LETTERED, EnumSet.of(PENDING));
    }

    public boolean canTransitionTo(JobStatus next) {
        return TRANSITIONS.get(this).contains(next);
    }

    public Set<JobStatus> allowedNext() {
        return Set.copyOf(TRANSITIONS.get(this));
    }

    /** Terminal for normal processing (recovery for FAILED / DEAD_LETTERED needs an explicit operation). */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == DEAD_LETTERED || this == CANCELLED;
    }

    public boolean isCancellable() {
        return canTransitionTo(CANCELLED);
    }

    /** Statuses from which a cancel request may succeed. */
    public static Set<JobStatus> cancellable() {
        Set<JobStatus> result = EnumSet.noneOf(JobStatus.class);
        for (JobStatus s : values()) {
            if (s.isCancellable()) {
                result.add(s);
            }
        }
        return result;
    }
}
