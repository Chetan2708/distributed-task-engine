package com.example.job_service.job.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Map;

@Data
public class CreateJobRequest {
    @NotBlank(message = "Job type is required")
    private String type;
    
    @NotNull(message = "Payload is required")
    private Map<String, Object> payload;
    
    @Min(value = 0, message = "Priority must be at least 0")
    private Integer priority;
    
    @Min(value = 0, message = "Max retries must be at least 0")
    @Max(value = 10, message = "Max retries cannot exceed 10")
    private Integer maxRetries;
}
