package com.example.job_service.job;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JobEntityTest {

    @Test
    void shouldAllowValidStateTransition() {
        JobEntity job = new JobEntity();
        job.setStatus(JobStatus.CREATED);
        job.transitionTo(JobStatus.QUEUED);
        assertEquals(JobStatus.QUEUED, job.getStatus());
    }

    @Test
    void shouldThrowOnInvalidStateTransition() {
        JobEntity job = new JobEntity();
        job.setStatus(JobStatus.SUCCESS);
        assertThrows(IllegalStateException.class, () -> job.transitionTo(JobStatus.PROCESSING));
    }
}
