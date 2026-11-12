package com.jobqueue.api.dto;

import com.jobqueue.common.model.JobType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record SubmitJobRequest(
        @NotNull(message = "jobType is required") JobType jobType,
        @NotNull(message = "payload is required") Map<String, Object> payload,
        @Min(value = 0, message = "maxRetries must be >= 0") @Max(value = 10, message = "maxRetries must be <= 10")
        Integer maxRetries,
        @Min(value = 1, message = "priority must be between 1 and 10") @Max(value = 10, message = "priority must be between 1 and 10")
        Integer priority) {
}
