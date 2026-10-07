package com.example.job_worker.job;

import com.example.job_worker.job.dto.JobEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdvancedWorkerFailureTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private ExecutorService executorService;
    
    @Mock
    private JobHandlerFactory jobHandlerFactory;
    
    @Mock
    private RLock lock;

    @InjectMocks
    private KafkaJobConsumer consumer;

    @Test
    void shouldHandleDuplicateEventAfterSuccess() throws Exception {
        // Setup
        UUID jobId = UUID.randomUUID();
        JobEvent event = new JobEvent(UUID.randomUUID(), jobId, "TEST");
        
        JobEntity completedJob = new JobEntity();
        completedJob.setId(jobId);
        completedJob.setStatus(JobStatus.SUCCESS); // Already successful
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(completedJob));
        
        // Execute
        consumer.consume(event.toString());
        
        // Verify
        verify(jobRepository, never()).save(any());
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    void shouldHandleLockExpirationOrDenial() throws Exception {
        UUID jobId = UUID.randomUUID();
        JobEvent event = new JobEvent(UUID.randomUUID(), jobId, "TEST");
        
        JobEntity job = new JobEntity();
        job.setId(jobId);
        job.setStatus(JobStatus.QUEUED);
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(redissonClient.getLock("job-lock:" + jobId)).thenReturn(lock);
        // Lock denied
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        
        // Execute
        consumer.consume(event.toString());
        
        // Verify we didn't try to process
        verify(executorService, never()).submit(any(Runnable.class));
    }
}
