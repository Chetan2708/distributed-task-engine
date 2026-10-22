package com.example.job_worker.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class JobCallbackNotifierTest {

    private HttpServer server;
    private int port;
    private JobCallbackNotifier notifier;
    private BlockingQueue<String> receivedPayloads;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        receivedPayloads = new LinkedBlockingQueue<>();

        server.createContext("/webhook/callback", exchange -> {
            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes());
                receivedPayloads.offer(body);
            }
            byte[] res = "{\"ok\":true}".getBytes();
            exchange.sendResponseHeaders(200, res.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(res);
            }
        });
        server.start();

        notifier = new JobCallbackNotifier(new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testSendsSuccessCompletionNotification() throws InterruptedException {
        JobEntity job = new JobEntity();
        job.setId(UUID.randomUUID());
        job.setType("DATA_SYNC");
        job.setStatus(JobStatus.SUCCESS);
        job.setResult(Map.of("recordsSynced", 100));
        job.setCallbackUrl("http://localhost:" + port + "/webhook/callback");
        job.setStartedAt(LocalDateTime.now().minusSeconds(2));
        job.setCompletedAt(LocalDateTime.now());
        job.setRetryCount(0);

        notifier.sendCompletionNotification(job).join();

        String payload = receivedPayloads.poll(3, TimeUnit.SECONDS);
        assertNotNull(payload);
        assertTrue(payload.contains("job.completed"));
        assertTrue(payload.contains("SUCCESS"));
        assertTrue(payload.contains("DATA_SYNC"));
        assertTrue(payload.contains("recordsSynced"));
        assertTrue(payload.contains(job.getId().toString()));
    }

    @Test
    void testSendsFailedNotification() throws InterruptedException {
        JobEntity job = new JobEntity();
        job.setId(UUID.randomUUID());
        job.setType("TEST");
        job.setStatus(JobStatus.FAILED);
        job.setErrorMessage("Target unreachable");
        job.setCallbackUrl("http://localhost:" + port + "/webhook/callback");
        job.setStartedAt(LocalDateTime.now().minusSeconds(1));
        job.setCompletedAt(LocalDateTime.now());
        job.setRetryCount(3);

        notifier.sendCompletionNotification(job).join();

        String payload = receivedPayloads.poll(3, TimeUnit.SECONDS);
        assertNotNull(payload);
        assertTrue(payload.contains("job.failed"));
        assertTrue(payload.contains("FAILED"));
        assertTrue(payload.contains("Target unreachable"));
    }

    @Test
    void testResolvesCallbackUrlFromPayload() throws InterruptedException {
        JobEntity job = new JobEntity();
        job.setId(UUID.randomUUID());
        job.setType("CUSTOM");
        job.setStatus(JobStatus.SUCCESS);
        job.setPayload(Map.of("callbackUrl", "http://localhost:" + port + "/webhook/callback"));

        notifier.sendCompletionNotification(job).join();

        String payload = receivedPayloads.poll(3, TimeUnit.SECONDS);
        assertNotNull(payload);
        assertTrue(payload.contains("job.completed"));
    }

    @Test
    void testGracefullyHandlesMissingOrInvalidUrl() {
        JobEntity jobNoUrl = new JobEntity();
        jobNoUrl.setStatus(JobStatus.SUCCESS);

        assertDoesNotThrow(() -> notifier.sendCompletionNotification(jobNoUrl).join());

        JobEntity jobBadUrl = new JobEntity();
        jobBadUrl.setStatus(JobStatus.SUCCESS);
        jobBadUrl.setCallbackUrl("ftp://invalid-protocol.com");

        assertDoesNotThrow(() -> notifier.sendCompletionNotification(jobBadUrl).join());
    }
}
