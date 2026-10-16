package com.example.job_worker.job.handler;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobResult {
    private boolean success;
    private String message;
    private Map<String, Object> data;

    public JobResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
