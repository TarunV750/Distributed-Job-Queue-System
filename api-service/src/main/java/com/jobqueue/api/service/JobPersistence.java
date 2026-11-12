package com.jobqueue.api.service;

import com.jobqueue.common.entity.Job;
import com.jobqueue.common.entity.OutboxEvent;
import com.jobqueue.common.model.JobType;
import com.jobqueue.common.repository.JobRepository;
import com.jobqueue.common.repository.OutboxEventRepository;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional core of submission. Kept in its own bean so the @Transactional proxy applies
 * and so a unique-key violation rolls back this transaction cleanly before the caller reacts to it.
 */
@Component
public class JobPersistence {

    public record NewJob(JobType type, Map<String, Object> payload, int maxRetries, int priority,
                         String idempotencyKey, String requestHash) {
    }

    private final JobRepository jobs;
    private final OutboxEventRepository outbox;
    private final Clock clock;

    public JobPersistence(JobRepository jobs, OutboxEventRepository outbox, Clock clock) {
        this.jobs = jobs;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Inserts the job and its outbox event atomically: either both exist or neither does. That is
     * what guarantees an accepted job is eventually published to the queue.
     */
    @Transactional
    public Job create(NewJob cmd) {
        var now = clock.instant();
        Job job = Job.create(UUID.randomUUID(), cmd.type(), cmd.payload(), cmd.maxRetries(), cmd.priority(),
                cmd.idempotencyKey(), cmd.requestHash(), now);
        // saveAndFlush so a unique-constraint violation surfaces here, inside this transaction.
        jobs.saveAndFlush(job);
        outbox.save(OutboxEvent.jobSubmitted(job, now));
        return job;
    }
}
