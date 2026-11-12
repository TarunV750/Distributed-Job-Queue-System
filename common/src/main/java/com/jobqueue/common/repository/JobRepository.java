package com.jobqueue.common.repository;

import com.jobqueue.common.entity.Job;
import com.jobqueue.common.model.JobStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    /**
     * Atomic conditional cancel. The status check and the write happen in one SQL statement, so a
     * worker claiming the job concurrently cannot be overwritten: exactly one side wins.
     *
     * @return 1 if cancelled, 0 if the job was missing or no longer in an eligible status
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Job j
               set j.status = :cancelled,
                   j.completedAt = :now,
                   j.version = j.version + 1
             where j.id = :id
               and j.status in :eligible
            """)
    int cancelIfEligible(@Param("id") UUID id,
                         @Param("now") Instant now,
                         @Param("cancelled") JobStatus cancelled,
                         @Param("eligible") Collection<JobStatus> eligible);
}
