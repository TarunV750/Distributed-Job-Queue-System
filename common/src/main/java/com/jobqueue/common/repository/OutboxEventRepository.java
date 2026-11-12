package com.jobqueue.common.repository;

import com.jobqueue.common.entity.OutboxEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    // Publisher queries (FOR UPDATE SKIP LOCKED) are added in Phase 2.
}
