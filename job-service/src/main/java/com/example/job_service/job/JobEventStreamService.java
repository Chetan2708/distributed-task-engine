package com.example.job_service.job;

import com.example.job_service.job.dto.JobStatusEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@EnableScheduling
public class JobEventStreamService {

    private static final Logger log = LoggerFactory.getLogger(JobEventStreamService.class);
    public static final String TOPIC_NAME = "jobflow:events";

    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;
    private final List<ClientSubscription> clients = new CopyOnWriteArrayList<>();

    private record ClientSubscription(
            SseEmitter emitter,
            String username,
            boolean isAdmin
    ) {}

    public JobEventStreamService(
            @Autowired(required = false) ObjectMapper objectMapper,
            @Autowired(required = false) RedissonClient redissonClient) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
        this.redissonClient = redissonClient;
    }

    @PostConstruct
    public void initRedisSubscription() {
        if (redissonClient != null) {
            try {
                RTopic topic = redissonClient.getTopic(TOPIC_NAME);
                topic.addListener(String.class, (channel, message) -> {
                    try {
                        JobStatusEvent event = objectMapper.readValue(message, JobStatusEvent.class);
                        broadcastLocally(event);
                    } catch (Exception e) {
                        log.error("Failed to parse Redis job event: {}", message, e);
                    }
                });
                log.info("Subscribed to Redis Pub/Sub topic '{}' for real-time SSE job events", TOPIC_NAME);
            } catch (Exception e) {
                log.warn("Could not subscribe to Redis topic '{}': {}", TOPIC_NAME, e.getMessage());
            }
        } else {
            log.info("RedissonClient not present; running in local in-memory event stream mode");
        }
    }

    public SseEmitter subscribe(String username, boolean isAdmin) {
        // 10 minutes timeout; browser will auto-reconnect on disconnect
        SseEmitter emitter = new SseEmitter(600_000L);
        ClientSubscription sub = new ClientSubscription(emitter, username, isAdmin);
        clients.add(sub);

        emitter.onCompletion(() -> clients.remove(sub));
        emitter.onTimeout(() -> {
            emitter.complete();
            clients.remove(sub);
        });
        emitter.onError(e -> {
            emitter.complete();
            clients.remove(sub);
        });

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "status", "CONNECTED",
                            "username", username,
                            "isAdmin", isAdmin,
                            "activeClients", clients.size()
                    )));
        } catch (IOException e) {
            clients.remove(sub);
        }

        return emitter;
    }

    public void publishEvent(JobStatusEvent event) {
        // 1. Deliver to local SSE clients
        broadcastLocally(event);

        // 2. Publish to Redis topic so other backend replicas receive it
        if (redissonClient != null) {
            try {
                String json = objectMapper.writeValueAsString(event);
                redissonClient.getTopic(TOPIC_NAME).publish(json);
            } catch (Exception e) {
                log.warn("Failed to publish job event to Redis Pub/Sub: {}", e.getMessage());
            }
        }
    }

    private void broadcastLocally(JobStatusEvent event) {
        List<ClientSubscription> deadClients = new CopyOnWriteArrayList<>();

        for (ClientSubscription client : clients) {
            if (client.isAdmin() || client.username().equals(event.submittedBy())) {
                try {
                    client.emitter().send(SseEmitter.event()
                            .name("job_event")
                            .data(event));
                } catch (Exception e) {
                    deadClients.add(client);
                }
            }
        }

        clients.removeAll(deadClients);
    }

    @Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        List<ClientSubscription> deadClients = new CopyOnWriteArrayList<>();

        for (ClientSubscription client : clients) {
            try {
                client.emitter().send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                deadClients.add(client);
            }
        }

        clients.removeAll(deadClients);
    }
}
