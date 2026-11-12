package com.jobqueue.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobqueue.api.dto.SubmitJobRequest;
import com.jobqueue.api.exception.IdempotencyConflictException;
import com.jobqueue.api.service.JobSubmissionService.SubmissionResult;
import com.jobqueue.common.entity.Job;
import com.jobqueue.common.model.JobType;
import com.jobqueue.common.repository.JobRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class JobSubmissionServiceTest {

    @Mock JobRepository jobs;
    @Mock JobPersistence persistence;

    private final ObjectMapper mapper = new ObjectMapper();
    private final RequestFingerprint fingerprint = new RequestFingerprint(mapper);
    private JobSubmissionService service;

    private static final Map<String, Object> PAYLOAD = Map.of("numbers", java.util.List.of(1, 2, 3));

    @BeforeEach
    void setUp() {
        service = new JobSubmissionService(new JobPayloadValidator(mapper, 4096), fingerprint, persistence, jobs,
                new SimpleMeterRegistry(), 3, 5);
    }

    private SubmitJobRequest request() {
        return new SubmitJobRequest(JobType.DATA_PROCESSING, PAYLOAD, null, null);
    }

    private Job jobWithHash(String key, String hash) {
        return Job.create(UUID.randomUUID(), JobType.DATA_PROCESSING, PAYLOAD, 3, 5, key, hash, Instant.now());
    }

    private String hashOfDefaultRequest() {
        return fingerprint.of(JobType.DATA_PROCESSING, PAYLOAD, 3, 5);
    }

    @Test
    void newJobIsPersistedWithDefaultsApplied() {
        Job created = jobWithHash(null, hashOfDefaultRequest());
        when(persistence.create(any())).thenReturn(created);

        SubmissionResult result = service.submit(request(), null);

        assertThat(result.replayed()).isFalse();
        assertThat(result.job()).isSameAs(created);
        verify(jobs, never()).findByIdempotencyKey(any());
    }

    @Test
    void sameKeyAndSameRequestReturnsOriginalWithoutCreating() {
        Job original = jobWithHash("k1", hashOfDefaultRequest());
        when(jobs.findByIdempotencyKey("k1")).thenReturn(Optional.of(original));

        SubmissionResult result = service.submit(request(), "k1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.job()).isSameAs(original);
        verify(persistence, never()).create(any());
    }

    @Test
    void sameKeyDifferentRequestIsRejected() {
        Job original = jobWithHash("k1", "some-other-hash");
        when(jobs.findByIdempotencyKey("k1")).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.submit(request(), "k1"))
                .isInstanceOf(IdempotencyConflictException.class);
        verify(persistence, never()).create(any());
    }

    @Test
    void concurrentDuplicateLosesTheRaceAndGetsTheWinnersJob() {
        Job winner = jobWithHash("k2", hashOfDefaultRequest());
        // First lookup: nothing yet. Insert then violates the unique index. Second lookup: the winner.
        when(jobs.findByIdempotencyKey("k2")).thenReturn(Optional.empty(), Optional.of(winner));
        when(persistence.create(any())).thenThrow(new DataIntegrityViolationException("uq_jobs_idempotency_key"));

        SubmissionResult result = service.submit(request(), "k2");

        assertThat(result.replayed()).isTrue();
        assertThat(result.job()).isSameAs(winner);
    }

    @Test
    void integrityViolationWithoutKeyIsNotSwallowed() {
        when(persistence.create(any())).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> service.submit(request(), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void blankKeyIsTreatedAsNoKey() {
        Job created = jobWithHash(null, hashOfDefaultRequest());
        when(persistence.create(any())).thenReturn(created);

        service.submit(request(), "   ");

        verify(jobs, never()).findByIdempotencyKey(any());
    }
}
