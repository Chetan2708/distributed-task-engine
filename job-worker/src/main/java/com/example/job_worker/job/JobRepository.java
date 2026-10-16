package com.example.job_worker.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<JobEntity, UUID> {

    @Query("SELECT j.status, COUNT(j) FROM JobEntity j GROUP BY j.status")
    List<Object[]> countJobsByStatus();

    @Query("SELECT j.type, COUNT(j) FROM JobEntity j GROUP BY j.type")
    List<Object[]> countJobsByType();

    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (completed_at - started_at))) FROM jobs WHERE completed_at IS NOT NULL AND started_at IS NOT NULL", nativeQuery = true)
    Double avgProcessingTimeSeconds();
}
