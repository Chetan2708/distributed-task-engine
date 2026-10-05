package com.example.job_service.job;

public enum JobStatus {
    CREATED,
    QUEUED,
    PROCESSING,
    SUCCESS,
    FAILED;

    public boolean canTransitionTo(JobStatus newStatus) {
        return switch (this) {
            case CREATED -> newStatus == QUEUED;
            case QUEUED -> newStatus == PROCESSING;
            case PROCESSING -> newStatus == SUCCESS || newStatus == FAILED;
            case SUCCESS, FAILED -> false;
        };
    }
}
