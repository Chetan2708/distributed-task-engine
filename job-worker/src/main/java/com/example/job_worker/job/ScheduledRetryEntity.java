package com.example.job_worker.job;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "scheduled_retries")
@Data
public class ScheduledRetryEntity {
    @Id
    private UUID id = UUID.randomUUID();
    
    private String topic;
    
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;
    
    private LocalDateTime executeAt;
}
