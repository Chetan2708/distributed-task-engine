package com.example.job_worker.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class JobCallbackNotifier {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public JobCallbackNotifier(@Autowired(required = false) ObjectMapper objectMapper) {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build(),
             objectMapper != null ? objectMapper : new ObjectMapper());
    }

    public JobCallbackNotifier(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public CompletableFuture<Void> sendCompletionNotification(JobEntity job) {
        String callbackUrl = resolveCallbackUrl(job);
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        URI uri;
        try {
            uri = URI.create(callbackUrl.trim());
            if (uri.getScheme() == null || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
                log.warn("Job {} callback skipped: URL '{}' does not use http/https protocol", job.getId(), callbackUrl);
                return CompletableFuture.completedFuture(null);
            }
        } catch (Exception e) {
            log.warn("Job {} callback skipped: malformed callbackUrl '{}': {}", job.getId(), callbackUrl, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("event", job.getStatus() == JobStatus.SUCCESS ? "job.completed" : "job.failed");
        body.put("jobId", job.getId() != null ? job.getId().toString() : null);
        body.put("type", job.getType());
        body.put("status", job.getStatus() != null ? job.getStatus().name() : null);
        body.put("result", job.getResult());
        body.put("errorMessage", job.getErrorMessage());
        body.put("retryCount", job.getRetryCount());
        body.put("startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null);
        body.put("completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : null);

        if (job.getStartedAt() != null && job.getCompletedAt() != null) {
            long durationMs = Duration.between(job.getStartedAt(), job.getCompletedAt()).toMillis();
            body.put("durationMs", durationMs);
        }

        try {
            String jsonPayload = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "JobFlow-Worker/1.0")
                    .header("X-Job-Id", job.getId() != null ? job.getId().toString() : "")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            log.info("Sending completion webhook for job {} to {}", job.getId(), uri);
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            log.info("Completion webhook delivered for job {} to {} with status {}",
                                    job.getId(), uri, response.statusCode());
                        } else {
                            log.warn("Completion webhook for job {} to {} returned non-2xx status: {}",
                                    job.getId(), uri, response.statusCode());
                        }
                    })
                    .exceptionally(ex -> {
                        log.warn("Failed to deliver completion webhook for job {} to {}: {}",
                                job.getId(), uri, ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.warn("Failed to serialize or initiate completion webhook for job {}: {}", job.getId(), e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    private String resolveCallbackUrl(JobEntity job) {
        if (job == null) return null;
        if (job.getCallbackUrl() != null && !job.getCallbackUrl().isBlank()) {
            return job.getCallbackUrl();
        }
        if (job.getPayload() != null) {
            if (job.getPayload().get("callbackUrl") != null) {
                return String.valueOf(job.getPayload().get("callbackUrl"));
            }
            if (job.getPayload().get("callback_url") != null) {
                return String.valueOf(job.getPayload().get("callback_url"));
            }
        }
        return null;
    }
}
