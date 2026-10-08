package com.example.job_service.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.google.gson.Gson;
import com.example.job_service.job.dto.JobEvent;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaJobProducer kafkaJobProducer;
    private final Gson gson = new Gson();

    @Scheduled(fixedDelayString = "${outbox.poll.delay:2000}")
    @Transactional
    public void publishOutboxEvents() {
        List<OutboxEventEntity> events = outboxEventRepository.findAndLockNextEvents(100);
        if (!events.isEmpty()) {
            log.info("Found {} outbox events to publish", events.size());
            for (OutboxEventEntity entity : events) {
                try {
                    JobEvent event = gson.fromJson(entity.getPayload(), JobEvent.class);
                    // Wait for Kafka to acknowledge the message synchronously
                    // If it fails, an exception is thrown, skipping the deletion
                    kafkaJobProducer.sendJobCreatedEvent(event).join();
                    outboxEventRepository.delete(entity);
                } catch (Exception e) {
                    log.error("Failed to process outbox event: {}", entity.getId(), e);
                }
            }
        }
    }
}
