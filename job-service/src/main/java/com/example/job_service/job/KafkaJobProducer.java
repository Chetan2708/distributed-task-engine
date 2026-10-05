package com.example.job_service.job;

import com.example.job_service.job.dto.JobEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KafkaJobProducer {

    private static final String TOPIC = "job-events";
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final com.google.gson.Gson gson = new com.google.gson.Gson();

    public KafkaJobProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendJobCreatedEvent(JobEvent event) {
        log.info("Sending job event to Kafka: {}", event);
        try {
            String jsonPayload = gson.toJson(event);
            kafkaTemplate.send(TOPIC, event.getJobId().toString(), jsonPayload);
        } catch (Exception e) {
            log.error("Failed to serialize JobEvent", e);
        }
    }
}
