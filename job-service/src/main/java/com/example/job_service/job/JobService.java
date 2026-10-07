package com.example.job_service.job;

import com.example.job_service.job.dto.CreateJobRequest;
import com.example.job_service.exception.JobNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final Gson gson = new Gson();

    @Transactional
    public JobEntity createJob(CreateJobRequest request) {
        JobEntity job = new JobEntity();
        job.setType(request.getType());
        job.setPayload(request.getPayload());
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().equals("anonymousUser")) {
            job.setUserId(auth.getName());
        }
        
        if (request.getPriority() != null) {
            job.setPriority(request.getPriority());
        }
        if (request.getMaxRetries() != null) {
            job.setMaxRetries(request.getMaxRetries());
        }
        
        JobEntity savedJob = jobRepository.save(job);
        
        // Save event to Outbox (Transactional Outbox Pattern)
        OutboxEventEntity outboxEvent = new OutboxEventEntity();
        outboxEvent.setAggregateId(savedJob.getId().toString());
        com.example.job_service.job.dto.JobEvent event = new com.example.job_service.job.dto.JobEvent(UUID.randomUUID(), savedJob.getId(), savedJob.getType());
        outboxEvent.setPayload(gson.toJson(event));
        outboxEventRepository.save(outboxEvent);
        
        return savedJob;
    }

    public JobEntity getJob(UUID id) {
        return jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    public Page<JobEntity> getAllJobs(Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = false;
        String username = null;
        
        if (auth != null) {
            username = auth.getName();
            for (GrantedAuthority authority : auth.getAuthorities()) {
                if (authority.getAuthority().equals("ROLE_ADMIN")) {
                    isAdmin = true;
                    break;
                }
            }
        }
        
        if (isAdmin || username == null || username.equals("anonymousUser")) {
            return jobRepository.findAll(pageable);
        } else {
            return jobRepository.findByUserId(username, pageable);
        }
    }



    public Map<String, Long> getStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", jobRepository.count());
        
        List<Object[]> statusCounts = jobRepository.countJobsByStatus();
        for (Object[] row : statusCounts) {
            JobStatus status = (JobStatus) row[0];
            Long count = (Long) row[1];
            
            switch (status) {
                case QUEUED: stats.put("queued", count); break;
                case PROCESSING: stats.put("processing", count); break;
                case SUCCESS: stats.put("successful", count); break;
                case ERROR: stats.put("failed", count); break;
                case FAILED: stats.put("dlq", count); break;
                default: break;
            }
        }
        
        // Ensure keys exist even if count is 0
        stats.putIfAbsent("queued", 0L);
        stats.putIfAbsent("processing", 0L);
        stats.putIfAbsent("successful", 0L);
        stats.putIfAbsent("failed", 0L);
        stats.putIfAbsent("dlq", 0L);
        
        return stats;
    }
}
