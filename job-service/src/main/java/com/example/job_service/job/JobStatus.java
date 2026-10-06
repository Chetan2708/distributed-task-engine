package com.example.job_service.job;

public enum JobStatus {
    CREATED,
    QUEUED,
    PROCESSING,
    SUCCESS,
    FAILED,
    ERROR;

    public boolean canTransitionTo(JobStatus newStatus) {
        return switch (this) {
            case CREATED, ERROR -> newStatus == QUEUED;
            case QUEUED -> newStatus == PROCESSING;
            case PROCESSING -> newStatus == SUCCESS || newStatus == FAILED || newStatus == ERROR;
            case SUCCESS, FAILED -> false;
        };
    }
}
