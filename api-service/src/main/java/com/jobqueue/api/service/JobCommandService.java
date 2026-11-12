package com.jobqueue.api.service;

import com.jobqueue.api.exception.JobNotFoundException;
import com.jobqueue.api.exception.JobStateConflictException;
import com.jobqueue.common.entity.Job;
import com.jobqueue.common.model.JobStatus;
import com.jobqueue.common.repository.JobRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobCommandService {

    private final JobRepository jobs;
    private final Clock clock;

    public JobCommandService(JobRepository jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
    }

    /**
     * Cancels a job that has not started running. The eligibility check lives inside the UPDATE's
     * WHERE clause, so racing a worker that is claiming the same job has exactly one winner.
     * A message already in SQS is not recalled: the worker sees CANCELLED and discards it (Phase 2).
     */
    @Transactional
    public Job cancel(UUID id) {
        int updated = jobs.cancelIfEligible(id, clock.instant(), JobStatus.CANCELLED, JobStatus.cancellable());
        Job job = jobs.findById(id).orElseThrow(() -> new JobNotFoundException(id));
        if (updated == 0) {
            throw new JobStateConflictException(id, job.getStatus(), "cancel");
        }
        return job;
    }
}
