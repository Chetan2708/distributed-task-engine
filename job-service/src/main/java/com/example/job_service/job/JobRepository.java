package com.example.job_service.job;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface JobRepository extends JpaRepository<JobEntity, UUID> {
}
