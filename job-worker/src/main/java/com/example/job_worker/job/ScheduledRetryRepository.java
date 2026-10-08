package com.example.job_worker.job;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduledRetryRepository extends JpaRepository<ScheduledRetryEntity, UUID> {
    List<ScheduledRetryEntity> findByExecuteAtBefore(LocalDateTime time);
    
    @Query(value = "SELECT * FROM scheduled_retries WHERE execute_at < :time ORDER BY execute_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<ScheduledRetryEntity> findAndLockNextRetries(@Param("time") LocalDateTime time, @Param("limit") int limit);
}
