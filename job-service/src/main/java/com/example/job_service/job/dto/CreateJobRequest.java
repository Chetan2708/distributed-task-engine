package com.example.job_service.job.dto;

import lombok.Data;
import java.util.Map;

@Data
public class CreateJobRequest {
    private String type;
    private Map<String, Object> payload;
    private Integer priority;
    private Integer maxRetries;
}
