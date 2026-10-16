package com.example.job_worker.job.handler;

import com.example.job_worker.job.JobEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EmailJobHandler implements JobHandler {
    @Override
    public String type() {
        return "EMAIL";
    }

    @Override
    public JobResult execute(JobEntity job) {
        log.info("Sending email for job {}", job.getId());
        try {
            // Simulate network call
            Thread.sleep(1500); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new JobResult(false, "Email dispatch interrupted");
        }
        return new JobResult(true, "[Simulated] Email dispatched — 1.5s network latency simulated");
    }
}
