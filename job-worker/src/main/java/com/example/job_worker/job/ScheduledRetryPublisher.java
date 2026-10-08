package com.example.job_worker.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledRetryPublisher {

    private final ScheduledRetryRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${retry.poll.delay:2000}")
    @Transactional
    public void publishDueRetries() {
        List<ScheduledRetryEntity> dueRetries = repository.findAndLockNextRetries(LocalDateTime.now(), 100);
        if (!dueRetries.isEmpty()) {
            log.info("Found {} durable retries to execute", dueRetries.size());
            for (ScheduledRetryEntity retry : dueRetries) {
                try {
                    kafkaTemplate.send(retry.getTopic(), retry.getPayload()).join();
                    repository.delete(retry);
                } catch (Exception e) {
                    log.error("Failed to publish retry event: {}", retry.getId(), e);
                }
            }
        }
    }
}
