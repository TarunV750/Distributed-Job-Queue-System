package com.jobqueue.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jobqueue.common.entity.JobAttempt;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttemptResponse(
        int attemptNumber,
        String workerId,
        String status,
        Instant startedAt,
        Instant finishedAt,
        Long executionDurationMs,
        String errorMessage) {

    public static AttemptResponse from(JobAttempt a) {
        return new AttemptResponse(a.getAttemptNumber(), a.getWorkerId(), a.getStatus(), a.getStartedAt(),
                a.getFinishedAt(), a.getExecutionDurationMs(), a.getErrorMessage());
    }
}
