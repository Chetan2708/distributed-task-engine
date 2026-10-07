package com.example.job_worker.job.handler;

import com.example.job_worker.job.JobEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TestJobHandler implements JobHandler {
    @Override
    public String type() {
        return "TEST";
    }

    @Override
    public JobResult execute(JobEntity job) {
        log.info("Executing TEST job {}", job.getId());
        return new JobResult(true, "Test job processed");
    }
}
