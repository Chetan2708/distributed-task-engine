package com.example.job_service.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Version;

@Data
@Entity
@Table(name = "jobs")
public class JobEntity {

    @Version
    private Long version;


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

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