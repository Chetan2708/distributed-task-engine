package com.example.job_worker.job.handler;

import com.example.job_worker.job.JobEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class HttpWebhookJobHandler implements JobHandler {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${jobflow.webhook.default-rate-limit-per-second:10}")
    private int defaultRateLimitPerSecond = 10;

    @Value("${jobflow.webhook.default-timeout-seconds:30}")
    private int defaultTimeoutSeconds = 30;

    @Autowired
    public HttpWebhookJobHandler(@Autowired(required = false) RedissonClient redissonClient,
                                 @Autowired(required = false) ObjectMapper objectMapper) {
        this(redissonClient,
             objectMapper != null ? objectMapper : new ObjectMapper(),
             HttpClient.newBuilder()
                     .followRedirects(HttpClient.Redirect.NORMAL)
                     .connectTimeout(Duration.ofSeconds(10))
                     .build());
    }

    public HttpWebhookJobHandler(RedissonClient redissonClient,
                                 ObjectMapper objectMapper,
                                 HttpClient httpClient) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.httpClient = httpClient;
    }

    @Override
    public String type() {
        return "HTTP_WEBHOOK";
    }

    @Override
    public JobResult execute(JobEntity job) {
        Map<String, Object> payload = job.getPayload();
        if (payload == null || !payload.containsKey("url")) {
            // Non-retryable configuration failure
            job.setMaxRetries(job.getRetryCount());
            return new JobResult(false, "Invalid webhook payload: 'url' is required");
        }

        String urlString = String.valueOf(payload.get("url")).trim();
        URI uri;
        try {
            uri = URI.create(urlString);
            if (uri.getScheme() == null || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("Only http and https protocols are supported");
            }
        } catch (Exception e) {
            job.setMaxRetries(job.getRetryCount());
            return new JobResult(false, "Invalid webhook URL '" + urlString + "': " + e.getMessage());
        }

        // Method (default POST)
        String method = payload.getOrDefault("method", "POST").toString().toUpperCase();

        // Timeout
        int timeoutSeconds = defaultTimeoutSeconds;
        if (payload.containsKey("timeoutSeconds") && payload.get("timeoutSeconds") instanceof Number number) {
            timeoutSeconds = number.intValue();
        }

        // Rate limit
        int rateLimit = defaultRateLimitPerSecond;
        if (payload.containsKey("rateLimitPerSecond") && payload.get("rateLimitPerSecond") instanceof Number number) {
            rateLimit = number.intValue();
        }

        String host = uri.getHost() != null ? uri.getHost() : "default";
        applyRateLimit(host, rateLimit);

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(timeoutSeconds));

            // Custom headers
            boolean hasContentType = false;
            if (payload.get("headers") instanceof Map<?, ?> customHeaders) {
                for (Map.Entry<?, ?> entry : customHeaders.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        String headerName = entry.getKey().toString();
                        String headerValue = entry.getValue().toString();
                        if (headerName.equalsIgnoreCase("Content-Type")) {
                            hasContentType = true;
                        }
                        try {
                            reqBuilder.header(headerName, headerValue);
                        } catch (IllegalArgumentException ex) {
                            log.warn("Skipping restricted header '{}': {}", headerName, ex.getMessage());
                        }
                    }
                }
            }

            // Body publisher
            HttpRequest.BodyPublisher bodyPublisher;
            if (payload.containsKey("body") && payload.get("body") != null) {
                Object bodyObj = payload.get("body");
                String bodyStr;
                if (bodyObj instanceof String s) {
                    bodyStr = s;
                } else {
                    bodyStr = objectMapper.writeValueAsString(bodyObj);
                }

                if (!hasContentType) {
                    reqBuilder.header("Content-Type", "application/json");
                }
                bodyPublisher = HttpRequest.BodyPublishers.ofString(bodyStr);
            } else if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                bodyPublisher = HttpRequest.BodyPublishers.noBody();
            } else {
                bodyPublisher = null;
            }

            if (bodyPublisher != null) {
                reqBuilder.method(method, bodyPublisher);
            } else {
                if ("GET".equals(method)) {
                    reqBuilder.GET();
                } else if ("DELETE".equals(method)) {
                    reqBuilder.DELETE();
                } else {
                    reqBuilder.method(method, HttpRequest.BodyPublishers.noBody());
                }
            }

            log.info("Dispatching webhook {} {} for job {}", method, uri, job.getId());
            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String responseBody = response.body();
            String truncatedBody = responseBody != null && responseBody.length() > 2000
                    ? responseBody.substring(0, 2000) + "... [truncated]"
                    : (responseBody != null ? responseBody : "");

            Map<String, Object> resultData = new HashMap<>();
            resultData.put("statusCode", statusCode);
            resultData.put("url", urlString);
            resultData.put("method", method);
            resultData.put("responseBody", truncatedBody);

            if (statusCode >= 200 && statusCode < 300) {
                log.info("Webhook succeeded with HTTP {} for job {}", statusCode, job.getId());
                return new JobResult(true, "HTTP " + statusCode + " OK", resultData);
            } else if (statusCode == 429 || statusCode >= 500) {
                // Retryable: 429 (Rate limited by target) or 5xx (Target server error)
                log.warn("Webhook failed with retryable HTTP {} for job {}", statusCode, job.getId());
                return new JobResult(false, "Retryable HTTP " + statusCode + ": " + truncatedBody, resultData);
            } else {
                // Non-retryable: 4xx client errors (400 Bad Request, 401 Unauthorized, 404 Not Found, etc.)
                log.error("Webhook failed with non-retryable HTTP {} for job {}", statusCode, job.getId());
                job.setMaxRetries(job.getRetryCount());
                return new JobResult(false, "Non-retryable HTTP " + statusCode + ": " + truncatedBody, resultData);
            }

        } catch (Exception e) {
            log.error("Webhook network exception for job {}: {}", job.getId(), e.getMessage());
            // Network errors (connection refused, timeouts) are retryable
            return new JobResult(false, "Webhook network error: " + e.getMessage());
        }
    }

    private void applyRateLimit(String host, int rateLimitPerSecond) {
        if (redissonClient == null || rateLimitPerSecond <= 0) {
            return;
        }
        try {
            String key = "ratelimit:webhook:" + host;
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
            rateLimiter.trySetRate(RateType.OVERALL, rateLimitPerSecond, 1, RateIntervalUnit.SECONDS);
            rateLimiter.acquire(1);
        } catch (Exception e) {
            log.warn("Rate limiting failed or skipped for host {}: {}", host, e.getMessage());
        }
    }
}
