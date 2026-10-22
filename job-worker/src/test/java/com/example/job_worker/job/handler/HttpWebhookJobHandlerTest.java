package com.example.job_worker.job.handler;

import com.example.job_worker.job.JobEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpWebhookJobHandlerTest {

    private HttpServer server;
    private int port;
    private RedissonClient redissonClient;
    private HttpWebhookJobHandler handler;
    private WebhookAliasHandler aliasHandler;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.start();

        redissonClient = Mockito.mock(RedissonClient.class);
        RRateLimiter rateLimiter = Mockito.mock(RRateLimiter.class);
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);

        handler = new HttpWebhookJobHandler(redissonClient, new ObjectMapper());
        aliasHandler = new WebhookAliasHandler(handler);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testSuccessfulWebhook() {
        server.createContext("/webhook/success", exchange -> {
            byte[] response = "{\"status\":\"ok\"}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        JobEntity job = new JobEntity();
        job.setMaxRetries(3);
        job.setRetryCount(0);

        Map<String, Object> payload = new HashMap<>();
        payload.put("url", "http://localhost:" + port + "/webhook/success");
        payload.put("method", "POST");
        payload.put("body", Map.of("event", "payment.succeeded"));
        job.setPayload(payload);

        JobResult result = handler.execute(job);

        assertTrue(result.isSuccess());
        assertEquals("HTTP 200 OK", result.getMessage());
        assertNotNull(result.getData());
        assertEquals(200, result.getData().get("statusCode"));
        assertEquals(3, job.getMaxRetries()); // retries not modified
    }

    @Test
    void test5xxServerErrorIsRetryable() {
        server.createContext("/webhook/fail-500", exchange -> {
            byte[] response = "Internal Server Error".getBytes();
            exchange.sendResponseHeaders(500, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        JobEntity job = new JobEntity();
        job.setMaxRetries(3);
        job.setRetryCount(0);

        Map<String, Object> payload = new HashMap<>();
        payload.put("url", "http://localhost:" + port + "/webhook/fail-500");
        job.setPayload(payload);

        JobResult result = handler.execute(job);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("500"));
        // Max retries must NOT be truncated so KafkaJobConsumer schedules retry!
        assertEquals(3, job.getMaxRetries());
    }

    @Test
    void test4xxClientErrorIsNotRetryable() {
        server.createContext("/webhook/not-found", exchange -> {
            byte[] response = "Not Found".getBytes();
            exchange.sendResponseHeaders(404, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        JobEntity job = new JobEntity();
        job.setMaxRetries(3);
        job.setRetryCount(1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("url", "http://localhost:" + port + "/webhook/not-found");
        job.setPayload(payload);

        JobResult result = handler.execute(job);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("404"));
        // Non-retryable error must truncate maxRetries to current retryCount
        assertEquals(1, job.getMaxRetries());
    }

    @Test
    void testMissingUrlFailsWithoutRetrying() {
        JobEntity job = new JobEntity();
        job.setMaxRetries(3);
        job.setRetryCount(0);
        job.setPayload(new HashMap<>());

        JobResult result = handler.execute(job);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("url"));
        assertEquals(0, job.getMaxRetries());
    }

    @Test
    void testRateLimitAppliesToHost() {
        server.createContext("/webhook/ping", exchange -> {
            exchange.sendResponseHeaders(200, -1);
        });

        JobEntity job = new JobEntity();
        job.setMaxRetries(3);
        job.setRetryCount(0);

        Map<String, Object> payload = new HashMap<>();
        payload.put("url", "http://localhost:" + port + "/webhook/ping");
        payload.put("rateLimitPerSecond", 5);
        job.setPayload(payload);

        handler.execute(job);

        verify(redissonClient).getRateLimiter("ratelimit:webhook:localhost");
    }

    @Test
    void testAliasHandlerDelegation() {
        server.createContext("/webhook/alias", exchange -> {
            exchange.sendResponseHeaders(200, -1);
        });

        JobEntity job = new JobEntity();
        job.setMaxRetries(3);
        job.setRetryCount(0);

        Map<String, Object> payload = new HashMap<>();
        payload.put("url", "http://localhost:" + port + "/webhook/alias");
        job.setPayload(payload);

        assertEquals("WEBHOOK", aliasHandler.type());
        JobResult result = aliasHandler.execute(job);
        assertTrue(result.isSuccess());
    }
}
