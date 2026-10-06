package com.example.job_worker.job;

import com.example.job_worker.job.dto.JobEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaJobConsumer {

    private final JobRepository jobRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;
    private final com.google.gson.Gson gson = new com.google.gson.Gson();
    
    // ScheduledExecutorService for non-blocking delays
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    @KafkaListener(topics = "job-events", groupId = "job-worker-group")
    public void consume(String message) {
        JobEvent event;
        try {
            event = gson.fromJson(message, JobEvent.class);
        } catch (Exception e) {
            log.error("Failed to parse Kafka message: {}", message, e);
            return;
        }
        log.info("Worker received event from Kafka: {}", event);

        try {
            ProcessedMessageEntity pm = new ProcessedMessageEntity();
            pm.setMessageId(event.getMessageId());
            pm.setJobId(event.getJobId());
            processedMessageRepository.saveAndFlush(pm);
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate message {}, ignoring", event.getMessageId());
            return;
        }

        JobEntity job = jobRepository.findById(event.getJobId()).orElse(null);
        if (job == null) {
            log.error("Job not found in database: {}", event.getJobId());
            return;
        }

        String lockKey = "lock:job:" + event.getJobId();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(30));

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("Job {} is currently locked by another worker. Skipping.", event.getJobId());
            return;
        }

        try {
            // First transition CREATED/ERROR -> QUEUED
            if (job.getStatus() == JobStatus.CREATED || job.getStatus() == JobStatus.ERROR) {
                job.setStatus(JobStatus.QUEUED);
                job = jobRepository.save(job);
                log.info("Job {} is now QUEUED (Attempt {})", job.getId(), job.getRetryCount() + 1);
            }

            // Then transition QUEUED -> PROCESSING
            if (job.getStatus() == JobStatus.QUEUED) {
                job.setStatus(JobStatus.PROCESSING);
                job.setStartedAt(LocalDateTime.now());
                job = jobRepository.save(job);
                log.info("Job {} is now PROCESSING", job.getId());
            }

            // Fake processing for 3 seconds
            Thread.sleep(3000);

            // Intentional failure for testing retry
            if (job.getPayload() != null && job.getPayload().containsKey("fail_attempts")) {
                int failAttempts = ((Double) job.getPayload().get("fail_attempts")).intValue();
                if (job.getRetryCount() < failAttempts) {
                    throw new RuntimeException("Intentional failure on attempt " + (job.getRetryCount() + 1));
                }
            }

            // Transition PROCESSING -> SUCCESS
            if (job.getStatus() == JobStatus.PROCESSING) {
                job.setStatus(JobStatus.SUCCESS);
                job.setCompletedAt(LocalDateTime.now());
                jobRepository.save(job);
                log.info("Job {} completed successfully (SUCCESS)", job.getId());
            }

        } catch (Exception e) {
            log.error("Failed to process job {}: {}", event.getJobId(), e.getMessage());
            
            job.setRetryCount(job.getRetryCount() + 1);
            if (job.getRetryCount() <= job.getMaxRetries()) {
                // Exponential backoff: 2^(retryCount-1) seconds
                long delaySeconds = (long) Math.pow(2, job.getRetryCount() - 1);
                
                log.warn("Job {} failed, marking as ERROR and scheduling retry... (Attempt {} of {}). Waiting {} seconds.", 
                        job.getId(), job.getRetryCount(), job.getMaxRetries(), delaySeconds);
                
                job.setStatus(JobStatus.ERROR);
                job.setErrorMessage(e.getMessage());
                job = jobRepository.save(job);
                
                // Re-publish to Kafka for retry after delay with a NEW messageId
                JobEvent retryEvent = new JobEvent(UUID.randomUUID(), event.getJobId(), event.getType());
                scheduler.schedule(() -> {
                    kafkaTemplate.send("job-events", gson.toJson(retryEvent));
                }, delaySeconds, TimeUnit.SECONDS);
            } else {
                log.error("Job {} failed permanently after {} attempts. Sending to DLQ.", job.getId(), job.getRetryCount());
                job.setStatus(JobStatus.FAILED);
                job.setErrorMessage(e.getMessage());
                job.setCompletedAt(LocalDateTime.now());
                jobRepository.save(job);
                
                // Send to DLQ
                kafkaTemplate.send("job-dlq", gson.toJson(event));
            }
        } finally {
            redisTemplate.delete(lockKey);
        }
    }
}
