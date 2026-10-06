package com.example.job_worker.job;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessageEntity, UUID> {
}
