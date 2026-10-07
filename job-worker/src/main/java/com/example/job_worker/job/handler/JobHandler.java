package com.example.job_worker.job.handler;

import com.example.job_worker.job.JobEntity;

public interface JobHandler {
    String type();
    JobResult execute(JobEntity job);
}
