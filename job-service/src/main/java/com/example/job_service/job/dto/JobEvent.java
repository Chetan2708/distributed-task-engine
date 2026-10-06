package com.example.job_service.job.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobEvent {
    private UUID messageId;
    private UUID jobId;
    private String type;
}
