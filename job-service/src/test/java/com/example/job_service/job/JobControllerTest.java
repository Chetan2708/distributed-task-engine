package com.example.job_service.job;

import com.example.job_service.job.dto.CreateJobRequest;
import com.example.job_service.job.JobStatus;
import com.example.job_service.job.dto.CreateJobResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock
    private JobService jobService;

    @InjectMocks
    private JobController jobController;

    @Test
    void createJob_success() throws Exception {
        CreateJobRequest request = new CreateJobRequest();
        request.setType("TEST");
        Map<String, Object> payload = new HashMap<>();
        payload.put("data", "test");
        request.setPayload(payload);

        JobEntity savedJob = new JobEntity();
        savedJob.setId(UUID.randomUUID());
        savedJob.setType("TEST");
        savedJob.setStatus(JobStatus.CREATED);

        when(jobService.createJob(any(CreateJobRequest.class))).thenReturn(savedJob);

        ResponseEntity<CreateJobResponse> response = jobController.createJob(request);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(savedJob.getId(), response.getBody().getJobId());
        assertEquals("CREATED", response.getBody().getStatus());
    }

    @Test
    void getJob_success() throws Exception {
        UUID jobId = UUID.randomUUID();
        JobEntity job = new JobEntity();
        job.setId(jobId);
        job.setStatus(JobStatus.PROCESSING);
        job.setType("TEST");

        when(jobService.getJob(jobId)).thenReturn(job);

        ResponseEntity<JobEntity> response = jobController.getJob(jobId);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(jobId, response.getBody().getId());
        assertEquals(JobStatus.PROCESSING, response.getBody().getStatus());
    }
}
