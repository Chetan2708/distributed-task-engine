package com.example.job_worker.job;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ScheduledRetryRepository extends JpaRepository<ScheduledRetryEntity, UUID> {
    List<ScheduledRetryEntity> findByExecuteAtBefore(LocalDateTime time);
}
