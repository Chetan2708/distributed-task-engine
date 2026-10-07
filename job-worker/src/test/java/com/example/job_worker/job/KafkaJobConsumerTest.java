package com.example.job_worker.job;

import com.example.job_worker.job.dto.JobEvent;
import com.example.job_worker.job.handler.JobHandler;
import com.example.job_worker.job.handler.TestJobHandler;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
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
    private ScheduledRetryRepository scheduledRetryRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private RLock lock;

    @Spy
    private Gson gson = new Gson();

    private KafkaJobConsumer kafkaJobConsumer;

    @BeforeEach
    void setUp() {
        JobHandler testHandler = new TestJobHandler();
        kafkaJobConsumer = new KafkaJobConsumer(
            jobRepository,
            processedMessageRepository,
            scheduledRetryRepository,
            kafkaTemplate,
            redissonClient,
            transactionTemplate,
            Collections.singletonList(testHandler)
        );
    }

    @Test
    void consume_processesJobSuccessfully() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        JobEvent event = new JobEvent(messageId, jobId, "TEST");
        String message = gson.toJson(event);

        JobEntity job = new JobEntity();
        job.setId(jobId);
        job.setStatus(JobStatus.CREATED);
        
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        kafkaJobConsumer.consume(message);

        // Assert
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(jobRepository, times(1)).findById(jobId);
            verify(jobRepository, times(2)).save(job);
            verify(lock, times(1)).unlock();
            assertEquals(JobStatus.SUCCESS, job.getStatus());
        });
    }

    @Test
    void consume_retriesOnFailure() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        JobEvent event = new JobEvent(messageId, jobId, "TEST");
        String message = gson.toJson(event);

        JobEntity job = new JobEntity();
        job.setId(jobId);
        job.setStatus(JobStatus.CREATED);
        
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("fail_attempts", 1.0); 
        job.setPayload(payload);
        
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(i -> i.getArguments()[0]);
        doAnswer(invocation -> {
            java.util.function.Consumer<TransactionStatus> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        // Act
        kafkaJobConsumer.consume(message);

        // Assert
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(jobRepository, times(1)).findById(jobId);
            verify(jobRepository, times(2)).save(job);
            verify(lock, times(1)).unlock();
            verify(scheduledRetryRepository, times(1)).save(any(ScheduledRetryEntity.class));
            assertEquals(JobStatus.ERROR, job.getStatus());
            assertEquals(1, job.getRetryCount());
        });
    }

    @Test
    void consume_sendsToDlqWhenMaxRetriesExceeded() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        JobEvent event = new JobEvent(messageId, jobId, "TEST");
        String message = gson.toJson(event);

        JobEntity job = new JobEntity();
        job.setId(jobId);
        job.setStatus(JobStatus.CREATED);
        job.setRetryCount(3); 
        job.setMaxRetries(3);
        
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("fail_attempts", 5.0);
        job.setPayload(payload);
        
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        kafkaJobConsumer.consume(message);

        // Assert
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(jobRepository, times(1)).findById(jobId);
            verify(jobRepository, times(2)).save(job);
            verify(lock, times(1)).unlock();
            verify(kafkaTemplate, times(1)).send(eq("job-dlq"), anyString());
            assertEquals(JobStatus.FAILED, job.getStatus());
            assertEquals(4, job.getRetryCount());
        });
    }

    @Test
    void consume_ignoresDuplicateMessageWhenAlreadySuccess() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        JobEvent event = new JobEvent(messageId, jobId, "TEST");
        String message = gson.toJson(event);

        JobEntity job = new JobEntity();
        job.setId(jobId);
        job.setStatus(JobStatus.SUCCESS);

        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        // Act
        kafkaJobConsumer.consume(message);

        // Assert
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(jobRepository, times(1)).findById(jobId);
            verify(jobRepository, never()).save(any());
            verify(lock, times(1)).unlock();
        });
    }

    @Test
    void consume_skipsWhenLockDenied() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        JobEvent event = new JobEvent(messageId, jobId, "TEST");
        String message = gson.toJson(event);

        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(false);

        // Act
        kafkaJobConsumer.consume(message);

        // Assert
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(jobRepository, never()).save(any());
            verify(lock, never()).unlock();
        });
    }
}
