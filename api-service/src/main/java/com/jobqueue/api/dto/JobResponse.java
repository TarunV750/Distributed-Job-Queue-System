package com.jobqueue.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jobqueue.common.entity.Job;
import com.jobqueue.common.model.JobStatus;
import com.jobqueue.common.model.JobType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobResponse(
        UUID jobId,
        JobType jobType,
        JobStatus status,
        int retryCount,
        int maxRetries,
        int priority,
        Instant createdAt,
        Instant queuedAt,
        Instant startedAt,
        Instant completedAt,
        Instant nextRetryAt,
        String workerId,
        Map<String, Object> result,
        String errorMessage) {

    public static JobResponse from(Job j) {
        return new JobResponse(j.getId(), j.getJobType(), j.getStatus(), j.getRetryCount(), j.getMaxRetries(),
                j.getPriority(), j.getCreatedAt(), j.getQueuedAt(), j.getStartedAt(), j.getCompletedAt(),
                j.getNextRetryAt(), j.getWorkerId(), j.getResult(), j.getErrorMessage());
    }
}
