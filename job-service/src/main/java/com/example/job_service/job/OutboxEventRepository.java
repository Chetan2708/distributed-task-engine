package com.example.job_service.job;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    
    @Query(value = "SELECT * FROM outbox_events ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEventEntity> findAndLockNextEvents(@Param("limit") int limit);
}
