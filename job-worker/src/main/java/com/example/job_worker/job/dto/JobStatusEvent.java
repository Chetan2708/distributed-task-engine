package com.example.job_worker.job.dto;

import java.util.UUID;

public record JobStatusEvent(
    UUID jobId,
    String status,
    String jobType,
    String submittedBy,
    String timestamp,
    String errorMessage
) {}
