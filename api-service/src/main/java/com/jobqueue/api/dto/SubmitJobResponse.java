package com.jobqueue.api.dto;

import com.jobqueue.common.model.JobStatus;
import java.time.Instant;
import java.util.UUID;

public record SubmitJobResponse(UUID jobId, JobStatus status, String message, Instant createdAt) {
}
