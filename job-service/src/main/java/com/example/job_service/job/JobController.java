package com.example.job_service.job;

import com.example.job_service.job.dto.CreateJobRequest;
import com.example.job_service.job.dto.CreateJobResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.frontend.url:http://localhost:5173}")
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<CreateJobResponse> createJob(@Valid @RequestBody CreateJobRequest request) {
        JobEntity job = jobService.createJob(request);
        CreateJobResponse response = new CreateJobResponse(job.getId(), job.getStatus().name());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobEntity> getJob(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.getJob(id));
    }

    @GetMapping
    public ResponseEntity<Page<JobEntity>> getAllJobs(Pageable pageable) {
        return ResponseEntity.ok(jobService.getAllJobs(pageable));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getJobStats() {
        return ResponseEntity.ok(jobService.getStats());
    }

}
