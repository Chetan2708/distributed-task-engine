package com.example.job_worker.job.handler;

import com.example.job_worker.job.JobEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReportJobHandler implements JobHandler {
    @Override
    public String type() {
        return "REPORT";
    }

    @Override
    public JobResult execute(JobEntity job) {
        log.info("Generating report for job {}", job.getId());
        try {
            // Simulate data aggregation
            Thread.sleep(5000); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new JobResult(false, "Report generation interrupted");
        }
        return new JobResult(true, "Report generated successfully");
    }
}
