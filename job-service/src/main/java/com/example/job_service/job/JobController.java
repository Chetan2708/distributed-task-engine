package com.example.job_service.job;

import com.example.job_service.job.dto.CreateJobRequest;
import com.example.job_service.job.dto.CreateJobResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.frontend.url:http://localhost:5173}")
public class JobController {

    private final JobService jobService;
    private final JobEventStreamService eventStreamService;

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

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamJobEvents(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return eventStreamService.subscribe(username, isAdmin);
    }
}
