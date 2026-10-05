package com.example.job_service.job;

import com.example.job_service.job.JobStatus;
import com.example.job_service.job.dto.CreateJobRequest;
import com.example.job_service.job.dto.JobEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private KafkaJobProducer kafkaJobProducer;

    @InjectMocks
    private JobService jobService;

    @Test
    void createJob_success() {
        // Arrange
        CreateJobRequest request = new CreateJobRequest();
        request.setType("TEST_JOB");
        Map<String, Object> payload = new HashMap<>();
        payload.put("data", "test");
        request.setPayload(payload);

        JobEntity savedJob = new JobEntity();
        savedJob.setId(UUID.randomUUID());
        savedJob.setType("TEST_JOB");
        savedJob.setStatus(JobStatus.CREATED);

        when(jobRepository.save(any(JobEntity.class))).thenReturn(savedJob);

        // Act
        JobEntity result = jobService.createJob(request);

        // Assert
        assertNotNull(result);
        assertEquals(JobStatus.CREATED, result.getStatus());
        assertEquals("TEST_JOB", result.getType());

        verify(jobRepository, times(1)).save(any(JobEntity.class));
        
        ArgumentCaptor<JobEvent> eventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        verify(kafkaJobProducer, times(1)).sendJobCreatedEvent(eventCaptor.capture());
        
        JobEvent capturedEvent = eventCaptor.getValue();
        assertEquals(savedJob.getId(), capturedEvent.getJobId());
        assertEquals("TEST_JOB", capturedEvent.getType());
    }

    @Test
    void getJob_found() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        JobEntity job = new JobEntity();
        job.setId(jobId);
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        // Act
        JobEntity result = jobService.getJob(jobId);

        // Assert
        assertNotNull(result);
        assertEquals(jobId, result.getId());
    }

    @Test
    void getJob_notFound() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> jobService.getJob(jobId));
    }
}
