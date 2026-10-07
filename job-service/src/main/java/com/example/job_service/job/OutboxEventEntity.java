package com.example.job_service.job;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventEntity {

    @Id
    private UUID id = UUID.randomUUID();

    private String aggregateId;
    
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload; // Store JSON representation of the event

    private LocalDateTime createdAt = LocalDateTime.now();
}
