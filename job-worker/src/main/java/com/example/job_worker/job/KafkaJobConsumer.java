package com.example.job_worker.job;

import com.example.job_worker.job.dto.JobEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaJobConsumer {

    private final JobRepository jobRepository;
    private final com.google.gson.Gson gson = new com.google.gson.Gson();

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

        JobEntity job = jobRepository.findById(event.getJobId()).orElse(null);
        if (job == null) {
            log.error("Job not found in database: {}", event.getJobId());
            return;
        }

        try {
            // First transition CREATED -> QUEUED
            if (job.getStatus() == JobStatus.CREATED) {
                job.setStatus(JobStatus.QUEUED);
                job = jobRepository.save(job);
                log.info("Job {} is now QUEUED", job.getId());
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

            // Transition PROCESSING -> SUCCESS
            if (job.getStatus() == JobStatus.PROCESSING) {
                job.setStatus(JobStatus.SUCCESS);
                job.setCompletedAt(LocalDateTime.now());
                jobRepository.save(job);
                log.info("Job {} completed successfully (SUCCESS)", job.getId());
            }

        } catch (Exception e) {
            log.error("Failed to process job {}: {}", event.getJobId(), e.getMessage());
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
    }
}
