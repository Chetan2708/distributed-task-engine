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
import org.springframework.transaction.support.TransactionTemplate;
import com.example.job_worker.job.handler.JobHandler;
import com.google.gson.Gson;

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
    private RLock lock;
    
    @Mock
    private ProcessedMessageRepository processedMessageRepository;
    
    @Mock
    private ScheduledRetryRepository scheduledRetryRepository;
    
    @Mock
    private TransactionTemplate transactionTemplate;
    
    @Mock
    private JobHandler jobHandler;

    private final Gson gson = new Gson();

    private KafkaJobConsumer consumer;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(jobHandler.type()).thenReturn("TEST");
        consumer = new KafkaJobConsumer(
            jobRepository,
            processedMessageRepository,
            scheduledRetryRepository,
            kafkaTemplate,
            redissonClient,
            transactionTemplate,
            java.util.Collections.singletonList(jobHandler)
        );
    }

    @Test
    void shouldHandleDuplicateEventAfterSuccess() throws Exception {
        // Setup
        UUID jobId = UUID.randomUUID();
        JobEvent event = new JobEvent(UUID.randomUUID(), jobId, "TEST");
        
        JobEntity completedJob = new JobEntity();
        completedJob.setId(jobId);
        completedJob.setStatus(JobStatus.SUCCESS); // Already successful
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(completedJob));
        when(redissonClient.getLock("lock:job:" + jobId)).thenReturn(lock);
        
        // Execute
        consumer.consume(gson.toJson(event));
        
        // Verify
        verify(jobRepository, never()).save(any());
        verify(redissonClient, times(1)).getLock(anyString());
    }

}
