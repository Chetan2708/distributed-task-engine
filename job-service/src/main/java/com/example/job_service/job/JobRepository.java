package com.example.job_service.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import java.util.List;

public interface JobRepository extends JpaRepository<JobEntity, UUID> {
    long countByStatus(JobStatus status);
    
    Page<JobEntity> findByUserId(String userId, Pageable pageable);
    
    @Query("SELECT j.status, COUNT(j) FROM JobEntity j GROUP BY j.status")
    List<Object[]> countJobsByStatus();
}
