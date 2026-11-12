package com.jobqueue.common.repository;

import com.jobqueue.common.entity.JobAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobAttemptRepository extends JpaRepository<JobAttempt, Long> {

    List<JobAttempt> findByJobIdOrderByAttemptNumberAsc(UUID jobId);
}
