package com.jobqueue.api.service;

import com.jobqueue.api.dto.SubmitJobRequest;
import com.jobqueue.api.exception.IdempotencyConflictException;
import com.jobqueue.common.entity.Job;
import com.jobqueue.common.repository.JobRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Orchestrates submission with idempotency. Deliberately NOT @Transactional itself: the unique-key
 * race is resolved by catching the violation after the inner transaction has already rolled back.
 */
@Service
public class JobSubmissionService {

    public record SubmissionResult(Job job, boolean replayed) {
    }

    private final JobPayloadValidator validator;
    private final RequestFingerprint fingerprint;
    private final JobPersistence persistence;
    private final JobRepository jobs;
    private final MeterRegistry metrics;
    private final int defaultMaxRetries;
    private final int defaultPriority;

    public JobSubmissionService(JobPayloadValidator validator, RequestFingerprint fingerprint,
                                JobPersistence persistence, JobRepository jobs, MeterRegistry metrics,
                                @Value("${jobs.defaults.max-retries:3}") int defaultMaxRetries,
                                @Value("${jobs.defaults.priority:5}") int defaultPriority) {
        this.validator = validator;
        this.fingerprint = fingerprint;
        this.persistence = persistence;
        this.jobs = jobs;
        this.metrics = metrics;
        this.defaultMaxRetries = defaultMaxRetries;
        this.defaultPriority = defaultPriority;
    }

    public SubmissionResult submit(SubmitJobRequest request, String idempotencyKey) {
        validator.validate(request.jobType(), request.payload());

        int maxRetries = request.maxRetries() != null ? request.maxRetries() : defaultMaxRetries;
        int priority = request.priority() != null ? request.priority() : defaultPriority;
        String hash = fingerprint.of(request.jobType(), request.payload(), maxRetries, priority);
        String key = normalize(idempotencyKey);

        if (key != null) {
            Optional<Job> existing = jobs.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                return replay(existing.get(), key, hash);
            }
        }

        try {
            Job created = persistence.create(new JobPersistence.NewJob(
                    request.jobType(), request.payload(), maxRetries, priority, key, hash));
            metrics.counter("jobs.submitted", "type", request.jobType().name()).increment();
            return new SubmissionResult(created, false);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent requests with the same key: the loser lands here and returns the winner's job.
            if (key == null) {
                throw e;
            }
            Job winner = jobs.findByIdempotencyKey(key).orElseThrow(() -> e);
            return replay(winner, key, hash);
        }
    }

    private SubmissionResult replay(Job existing, String key, String hash) {
        if (!hash.equals(existing.getRequestHash())) {
            throw new IdempotencyConflictException(key);
        }
        return new SubmissionResult(existing, true);
    }

    private static String normalize(String key) {
        return key == null || key.isBlank() ? null : key.trim();
    }
}
