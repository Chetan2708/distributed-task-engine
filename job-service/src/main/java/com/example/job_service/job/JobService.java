package com.example.job_service.job;

import com.example.job_service.job.dto.CreateJobRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final KafkaJobProducer kafkaJobProducer;

    public JobEntity createJob(CreateJobRequest request) {
        JobEntity job = new JobEntity();
        job.setType(request.getType());
        job.setPayload(request.getPayload());
        
        if (request.getPriority() != null) {
            job.setPriority(request.getPriority());
        }
        if (request.getMaxRetries() != null) {
            job.setMaxRetries(request.getMaxRetries());
        }
        
        JobEntity savedJob = jobRepository.save(job);
        
        // Send event to Kafka
        kafkaJobProducer.sendJobCreatedEvent(new com.example.job_service.job.dto.JobEvent(savedJob.getId(), savedJob.getType()));
        
        return savedJob;
    }

    public JobEntity getJob(UUID id) {
        return jobRepository.findById(id).orElseThrow(() -> new RuntimeException("Job not found"));
    }

    public List<JobEntity> getAllJobs() {
        return jobRepository.findAll();
    }

    public JobEntity updateStatus(UUID id, JobStatus newStatus) {
        JobEntity job = getJob(id);
        
        if (!job.getStatus().canTransitionTo(newStatus)) {
            throw new IllegalStateException("Invalid state transition from " + job.getStatus() + " to " + newStatus);
        }
        
        job.setStatus(newStatus);
        
        if (newStatus == JobStatus.PROCESSING) {
            job.setStartedAt(java.time.LocalDateTime.now());
        } else if (newStatus == JobStatus.SUCCESS || newStatus == JobStatus.FAILED) {
            job.setCompletedAt(java.time.LocalDateTime.now());
        }
        
        return jobRepository.save(job);
    }
}
