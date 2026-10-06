package com.example.job_worker.job;

import com.example.job_worker.job.JobStatus;
import com.example.job_worker.job.dto.JobEvent;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaJobConsumerTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private ProcessedMessageRepository processedMessageRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private Gson gson = new Gson();

    @InjectMocks
    private KafkaJobConsumer kafkaJobConsumer;

    @Test
    void consume_processesJobSuccessfully() throws InterruptedException {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        JobEvent event = new JobEvent(messageId, jobId, "TEST");
        String message = gson.toJson(event);

        JobEntity job = new JobEntity();
        job.setId(jobId);
        job.setStatus(JobStatus.CREATED);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class))).thenReturn(true);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        kafkaJobConsumer.consume(message);

        // Assert
        verify(jobRepository, times(1)).findById(jobId);
        verify(jobRepository, times(3)).save(job);
        
        // Final state should be SUCCESS
        assertEquals(JobStatus.SUCCESS, job.getStatus());
    }

    @Test
    void consume_retriesOnFailure() throws InterruptedException {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        JobEvent event = new JobEvent(messageId, jobId, "TEST");
        String message = gson.toJson(event);

        JobEntity job = new JobEntity();
        job.setId(jobId);
        job.setStatus(JobStatus.CREATED);
        
        // Setup payload to trigger fail on first attempt
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("fail_attempts", 1.0); // JSON numbers map to Double by Gson
        job.setPayload(payload);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class))).thenReturn(true);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        kafkaJobConsumer.consume(message);

        // Assert
        verify(jobRepository, times(1)).findById(jobId);
        // Save is called: 1 (CREATED -> QUEUED), 2 (QUEUED -> PROCESSING), 3 (PROCESSING -> ERROR)
        verify(jobRepository, times(3)).save(job);
        
        Awaitility.await()
            .atMost(3, TimeUnit.SECONDS)
            .untilAsserted(() -> verify(kafkaTemplate, times(1)).send(eq("job-events"), anyString()));
        
        assertEquals(JobStatus.ERROR, job.getStatus());
        assertEquals(1, job.getRetryCount());
    }

    @Test
    void consume_sendsToDlqWhenMaxRetriesExceeded() throws InterruptedException {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        JobEvent event = new JobEvent(messageId, jobId, "TEST");
        String message = gson.toJson(event);

        JobEntity job = new JobEntity();
        job.setId(jobId);
        job.setStatus(JobStatus.CREATED);
        job.setRetryCount(3); // Already at max
        job.setMaxRetries(3);
        
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("fail_attempts", 5.0);
        job.setPayload(payload);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class))).thenReturn(true);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        kafkaJobConsumer.consume(message);

        // Assert
        verify(jobRepository, times(1)).findById(jobId);
        verify(jobRepository, times(3)).save(job);
        
        // Should send to DLQ immediately (not scheduled)
        verify(kafkaTemplate, times(1)).send(eq("job-dlq"), anyString());
        // Should NOT send to job-events
        verify(kafkaTemplate, never()).send(eq("job-events"), anyString());
        
        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals(4, job.getRetryCount());
    }

    @Test
    void consume_ignoresDuplicateMessage() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        JobEvent event = new JobEvent(messageId, jobId, "TEST");
        String message = gson.toJson(event);

        // Simulate duplicate by throwing DataIntegrityViolationException on saveAndFlush
        when(processedMessageRepository.saveAndFlush(any(ProcessedMessageEntity.class)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate"));

        // Act
        kafkaJobConsumer.consume(message);

        // Assert
        // Should ignore and return early, so jobRepository.findById is NEVER called
        verify(jobRepository, never()).findById(any());
        verify(jobRepository, never()).save(any());
    }

    @Test
    void consume_skipsWhenLockDenied() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        JobEvent event = new JobEvent(messageId, jobId, "TEST");
        String message = gson.toJson(event);

        JobEntity job = new JobEntity();
        job.setId(jobId);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class))).thenReturn(false);

        // Act
        kafkaJobConsumer.consume(message);

        // Assert
        // Should return early, so job status is not changed and save is never called
        verify(jobRepository, never()).save(any());
    }
}
