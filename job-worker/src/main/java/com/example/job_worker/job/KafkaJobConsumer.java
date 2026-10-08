package com.example.job_worker.job;

import com.example.job_worker.job.dto.JobEvent;
import com.example.job_worker.job.handler.JobHandler;
import com.example.job_worker.job.handler.JobResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class KafkaJobConsumer {

    private final JobRepository jobRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ScheduledRetryRepository scheduledRetryRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;
    private final com.google.gson.Gson gson = new com.google.gson.Gson();
    
    private final Map<String, JobHandler> handlers = new HashMap<>();

    public KafkaJobConsumer(JobRepository jobRepository,
                            ProcessedMessageRepository processedMessageRepository,
                            ScheduledRetryRepository scheduledRetryRepository,
                            KafkaTemplate<String, String> kafkaTemplate,
                            RedissonClient redissonClient,
                            TransactionTemplate transactionTemplate,
                            List<JobHandler> jobHandlers) {
        this.jobRepository = jobRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.scheduledRetryRepository = scheduledRetryRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.redissonClient = redissonClient;
        this.transactionTemplate = transactionTemplate;
        
        for (JobHandler handler : jobHandlers) {
            this.handlers.put(handler.type(), handler);
        }
    }

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

        JobHandler handler = handlers.get(event.getType());
        if (handler == null) {
            log.error("No handler configured for job type: {}. Skipping.", event.getType());
            return;
        }

        // Process synchronously to ensure Kafka offset is only committed 
        // AFTER the job has safely executed or a durable retry is scheduled.
        processJob(event, handler);
    }

    private void processJob(JobEvent event, JobHandler handler) {
        String lockKey = "lock:job:" + event.getJobId();
        RLock lock = redissonClient.getLock(lockKey);
        
        // Wait for the lock if another worker is processing (e.g. during a rebalance)
        // This ensures we don't skip the message and prematurely commit the offset.
        lock.lock();

        JobEntity job = null;
        try {
            job = jobRepository.findById(event.getJobId()).orElse(null);
            if (job == null) {
                log.error("Job not found in database: {}", event.getJobId());
                return;
            }

            if (job.getStatus() == JobStatus.SUCCESS || job.getStatus() == JobStatus.FAILED) {
                log.info("Job {} is already completed ({}). Ignoring duplicate message.", job.getId(), job.getStatus());
                return;
            }

            try {
                job.transitionTo(JobStatus.PROCESSING);
                job = jobRepository.save(job);
                log.info("Job {} claimed and is now PROCESSING (Attempt {})", job.getId(), job.getRetryCount() + 1);
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                log.warn("Job {} was claimed by another worker concurrently. Skipping.", event.getJobId());
                return;
            }

            // --- ACTUAL PROCESSING ---
            log.info("Executing handler {} for job {}", handler.type(), job.getId());
            JobResult result = handler.execute(job);

            if (job.getPayload() != null && job.getPayload().containsKey("fail_attempts")) {
                int failAttempts = ((Double) job.getPayload().get("fail_attempts")).intValue();
                if (job.getRetryCount() < failAttempts) {
                    throw new RuntimeException("Intentional failure on attempt " + (job.getRetryCount() + 1));
                }
            }

            if (result.isSuccess()) {
                job.transitionTo(JobStatus.SUCCESS);
                jobRepository.save(job);
                log.info("Job {} completed successfully: {}", job.getId(), result.getMessage());

                try {
                    ProcessedMessageEntity pm = new ProcessedMessageEntity();
                    pm.setMessageId(event.getMessageId());
                    pm.setJobId(event.getJobId());
                    processedMessageRepository.saveAndFlush(pm);
                } catch (DataIntegrityViolationException e) {
                    // Ignore
                }
            } else {
                throw new RuntimeException("Handler failed: " + result.getMessage());
            }

        } catch (Exception e) {
            log.error("Failed to process job {}: {}", event.getJobId(), e.getMessage());
            
            if (job != null) {
                job.setRetryCount(job.getRetryCount() + 1);
                if (job.getRetryCount() <= job.getMaxRetries()) {
                    long delaySeconds = (long) Math.pow(2, job.getRetryCount() - 1);
                    log.warn("Job {} failed, marking as ERROR and scheduling durable retry... (Attempt {} of {}). Waiting {} seconds.", 
                            job.getId(), job.getRetryCount(), job.getMaxRetries(), delaySeconds);
                    
                    job.transitionTo(JobStatus.ERROR);
                    job.setErrorMessage(e.getMessage());
                    
                    JobEvent retryEvent = new JobEvent(UUID.randomUUID(), event.getJobId(), event.getType());
                    final JobEntity finalJob = job;
                    
                    try {
                        transactionTemplate.executeWithoutResult(status -> {
                            jobRepository.save(finalJob);
                            ScheduledRetryEntity retry = new ScheduledRetryEntity();
                            retry.setTopic("job-events");
                            retry.setPayload(gson.toJson(retryEvent));
                            retry.setExecuteAt(LocalDateTime.now().plusSeconds(delaySeconds));
                            scheduledRetryRepository.save(retry);
                        });
                    } catch (org.springframework.orm.ObjectOptimisticLockingFailureException oolfe) {
                        log.warn("Failed to save ERROR state due to optimistic locking. Job {}", job.getId());
                    }
                    
                } else {
                    log.error("Job {} failed permanently after {} attempts. Sending to DLQ.", job.getId(), job.getRetryCount());
                    job.transitionTo(JobStatus.FAILED);
                    job.setErrorMessage(e.getMessage());
                    try {
                        kafkaTemplate.send("job-dlq", gson.toJson(event)).join();
                        jobRepository.save(job);
                    } catch (Exception ex) {
                        log.error("Failed to publish to DLQ or save FAILED state for job {}", job.getId(), ex);
                        throw new RuntimeException("DLQ publish failed", ex);
                    }
                }
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
