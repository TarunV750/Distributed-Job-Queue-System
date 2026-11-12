package com.jobqueue.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobqueue.common.entity.Job;
import com.jobqueue.common.exception.InvalidStateTransitionException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class JobStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING,QUEUED", "PENDING,CANCELLED",
            "QUEUED,RUNNING", "QUEUED,CANCELLED", "QUEUED,DEAD_LETTERED",
            "RUNNING,COMPLETED", "RUNNING,RETRYING", "RUNNING,FAILED", "RUNNING,DEAD_LETTERED",
            "RETRYING,RUNNING", "RETRYING,CANCELLED", "RETRYING,DEAD_LETTERED",
            "FAILED,PENDING", "DEAD_LETTERED,PENDING"
    })
    void allowsDocumentedTransitions(JobStatus from, JobStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "COMPLETED,RUNNING", "COMPLETED,PENDING", "CANCELLED,QUEUED", "CANCELLED,RUNNING",
            "DEAD_LETTERED,COMPLETED", "DEAD_LETTERED,RUNNING", "FAILED,COMPLETED",
            "PENDING,RUNNING", "PENDING,COMPLETED", "QUEUED,COMPLETED", "RUNNING,CANCELLED",
            "RUNNING,QUEUED"
    })
    void rejectsInvalidTransitions(JobStatus from, JobStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(JobStatus.class)
    void noStatusTransitionsToItself(JobStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @Test
    void cancellableStatusesAreExactlyTheNonRunningNonTerminalOnes() {
        assertThat(JobStatus.cancellable())
                .containsExactlyInAnyOrder(JobStatus.PENDING, JobStatus.QUEUED, JobStatus.RETRYING);
    }

    @Test
    void terminalStatuses() {
        assertThat(JobStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(JobStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(JobStatus.RUNNING.isTerminal()).isFalse();
        assertThat(JobStatus.PENDING.isTerminal()).isFalse();
    }

    @Test
    void jobEntityEnforcesTransitions() {
        Job job = Job.create(UUID.randomUUID(), JobType.EMAIL, Map.of("k", "v"), 3, 5, null, null, Instant.now());
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);

        job.transitionTo(JobStatus.QUEUED);
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);

        assertThatThrownBy(() -> job.transitionTo(JobStatus.COMPLETED))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("QUEUED -> COMPLETED");
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
    }
}
