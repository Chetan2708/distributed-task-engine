package com.example.job_worker.job;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedMessageEntity {

    @Id
    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "processed_at")
    private LocalDateTime processedAt = LocalDateTime.now();
}
