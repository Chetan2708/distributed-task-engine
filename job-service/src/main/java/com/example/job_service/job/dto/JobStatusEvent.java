package com.example.job_service.job.dto;

import java.util.UUID;

public record JobStatusEvent(
    UUID jobId,
    String status,
    String jobType,
    String submittedBy,
    String timestamp,
    String errorMessage
) {}
