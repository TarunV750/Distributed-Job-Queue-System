package com.jobqueue.api.service;

import com.jobqueue.api.exception.JobNotFoundException;
import com.jobqueue.common.entity.Job;
import com.jobqueue.common.entity.JobAttempt;
import com.jobqueue.common.model.JobStatus;
import com.jobqueue.common.model.JobType;
import com.jobqueue.common.repository.JobAttemptRepository;
import com.jobqueue.common.repository.JobRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class JobQueryService {

    private final JobRepository jobs;
    private final JobAttemptRepository attempts;

    public JobQueryService(JobRepository jobs, JobAttemptRepository attempts) {
        this.jobs = jobs;
        this.attempts = attempts;
    }

    public Job get(UUID id) {
        return jobs.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    public Page<Job> list(JobStatus status, JobType type, Pageable pageable) {
        // Specifications skip null filters entirely, avoiding "null IS NULL" typed-parameter problems on PostgreSQL.
        Specification<Job> spec = Specification.where(null);
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (type != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("jobType"), type));
        }
        return jobs.findAll(spec, pageable);
    }

    public List<JobAttempt> attempts(UUID jobId) {
        if (!jobs.existsById(jobId)) {
            throw new JobNotFoundException(jobId);
        }
        return attempts.findByJobIdOrderByAttemptNumberAsc(jobId);
    }
}
