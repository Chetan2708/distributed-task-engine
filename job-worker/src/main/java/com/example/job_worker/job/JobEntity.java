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
import java.util.UUID;

@Entity
@Table(name = "jobs")
@Data
public class JobEntity {

    @Id
    private UUID id = UUID.randomUUID();

    private String type;

    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.CREATED;

    private Integer priority = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    private Object payload;

    @JdbcTypeCode(SqlTypes.JSON)
    private Object result;

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
}
