package com.example.job_worker.job;

public enum JobStatus {
    CREATED,
    QUEUED,
    PROCESSING,
    SUCCESS,
    FAILED,
    ERROR;

    public boolean canTransitionTo(JobStatus newStatus) {
        return switch (this) {
            case CREATED, ERROR -> newStatus == QUEUED || newStatus == PROCESSING;
            case QUEUED -> newStatus == PROCESSING;
            case PROCESSING -> newStatus == PROCESSING || newStatus == SUCCESS || newStatus == FAILED || newStatus == ERROR;
            case SUCCESS, FAILED -> false;
        };
    }
}
