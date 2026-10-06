package com.example.job_service.job;

import com.example.job_service.job.dto.CreateJobRequest;
import com.example.job_service.job.dto.CreateJobResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<CreateJobResponse> createJob(@RequestBody CreateJobRequest request) {
        JobEntity job = jobService.createJob(request);
        CreateJobResponse response = new CreateJobResponse(job.getId(), job.getStatus().name());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobEntity> getJob(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.getJob(id));
    }

    @GetMapping
    public ResponseEntity<List<JobEntity>> getAllJobs() {
        return ResponseEntity.ok(jobService.getAllJobs());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getJobStats() {
        return ResponseEntity.ok(jobService.getStats());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<JobEntity> updateJobStatus(@PathVariable UUID id, @RequestBody com.example.job_service.job.dto.UpdateJobStatusRequest request) {
        JobStatus newStatus = JobStatus.valueOf(request.getStatus().toUpperCase());
        return ResponseEntity.ok(jobService.updateStatus(id, newStatus));
    }
}
