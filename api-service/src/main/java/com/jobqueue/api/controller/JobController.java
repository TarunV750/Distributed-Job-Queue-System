package com.jobqueue.api.controller;

import com.jobqueue.api.dto.AttemptResponse;
import com.jobqueue.api.dto.JobResponse;
import com.jobqueue.api.dto.PageResponse;
import com.jobqueue.api.dto.SubmitJobRequest;
import com.jobqueue.api.dto.SubmitJobResponse;
import com.jobqueue.api.service.JobCommandService;
import com.jobqueue.api.service.JobQueryService;
import com.jobqueue.api.service.JobSubmissionService;
import com.jobqueue.api.service.JobSubmissionService.SubmissionResult;
import com.jobqueue.common.model.JobStatus;
import com.jobqueue.common.model.JobType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs")
public class JobController {

    private static final int MAX_PAGE_SIZE = 100;
    /** Whitelist: never sort by a client-supplied arbitrary property name. */
    private static final Set<String> SORTABLE = Set.of("createdAt", "priority", "status", "completedAt");

    private final JobSubmissionService submission;
    private final JobQueryService queries;
    private final JobCommandService commands;

    public JobController(JobSubmissionService submission, JobQueryService queries, JobCommandService commands) {
        this.submission = submission;
        this.queries = queries;
        this.commands = commands;
    }

    @PostMapping
    @Operation(summary = "Submit a job",
            description = "Returns 202 for a new job. Send an Idempotency-Key header to make retries safe: "
                    + "a repeated key with the same request returns the original job with 200; "
                    + "the same key with a different request returns 409.")
    public ResponseEntity<SubmitJobResponse> submit(
            @Valid @RequestBody SubmitJobRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        SubmissionResult result = submission.submit(request, idempotencyKey);
        var job = result.job();
        var body = new SubmitJobResponse(job.getId(), job.getStatus(),
                result.replayed() ? "Job already accepted (idempotent replay)" : "Job accepted",
                job.getCreatedAt());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get job status and result")
    public JobResponse get(@PathVariable UUID jobId) {
        return JobResponse.from(queries.get(jobId));
    }

    @GetMapping
    @Operation(summary = "List jobs", description = "Filter by status/jobType. sort=<field>,<asc|desc> where field is one of "
            + "createdAt, priority, status, completedAt.")
    public PageResponse<JobResponse> list(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) JobType jobType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        if (page < 0) {
            page = 0;
        }
        size = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        var pageable = PageRequest.of(page, size, parseSort(sort));
        return PageResponse.of(queries.list(status, jobType, pageable), JobResponse::from);
    }

    @PostMapping("/{jobId}/cancel")
    @Operation(summary = "Cancel a job that has not started running")
    public JobResponse cancel(@PathVariable UUID jobId) {
        return JobResponse.from(commands.cancel(jobId));
    }

    @GetMapping("/{jobId}/attempts")
    @Operation(summary = "Execution attempt history")
    public List<AttemptResponse> attempts(@PathVariable UUID jobId) {
        return queries.attempts(jobId).stream().map(AttemptResponse::from).toList();
    }

    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SORTABLE.contains(field)) {
            field = "createdAt";
        }
        Sort.Direction dir = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        // Secondary key makes pagination stable when the primary values tie.
        return Sort.by(dir, field).and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
