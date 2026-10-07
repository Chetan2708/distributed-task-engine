package com.example.job_worker.job;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Version;

@Entity
@Table(name = "jobs")
@Data
public class JobEntity {

    @Version
    private Long version;


    @Id
    private UUID id = UUID.randomUUID();

    private String type;

    @Column(name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.CREATED;

    private Integer priority = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> payload;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> result;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    private Integer maxRetries = 3;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message")
    private String errorMessage;

    public void transitionTo(JobStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException("Invalid state transition from " + this.status + " to " + newStatus);
        }
        this.status = newStatus;
        if (newStatus == JobStatus.PROCESSING) {
            this.startedAt = LocalDateTime.now();
        } else if (newStatus == JobStatus.SUCCESS || newStatus == JobStatus.FAILED) {
            this.completedAt = LocalDateTime.now();
        }
    }
}
