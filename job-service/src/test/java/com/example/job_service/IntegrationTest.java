package com.example.job_service;

import com.example.job_service.job.JobController;
import com.example.job_service.job.JobEntity;
import com.example.job_service.job.JobRepository;
import com.example.job_service.job.dto.CreateJobRequest;
import com.example.job_service.job.dto.JobEvent;
import com.google.gson.Gson;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.Collections;
import com.example.job_service.job.JobStatus;
import com.example.job_service.job.dto.CreateJobResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.flyway.enabled=false",
    "outbox.poll.delay=500",
    "spring.main.allow-bean-definition-overriding=true"
})
@EmbeddedKafka(partitions = 1, topics = { "job-events" })
public class IntegrationTest {

    @Autowired
    private JobController jobController;

    @Autowired
    private JobRepository jobRepository;

    private Gson gson = new Gson();

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "test-group", "false", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new KafkaConsumer<>(consumerProps, new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(Collections.singletonList("job-events"));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void testCreateJob_savedToDb_and_sentToKafka() throws Exception {
        // 1. Send Request and Verify API Response
        CreateJobRequest request = new CreateJobRequest();
        request.setType("INTEGRATION_TEST");
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");
        request.setPayload(payload);

        ResponseEntity<CreateJobResponse> response = jobController.createJob(request);
        assertEquals(200, response.getStatusCode().value());

        CreateJobResponse responseBody = response.getBody();
        assertNotNull(responseBody);
        UUID jobId = responseBody.getJobId();

        // 3. Verify Database State
        JobEntity savedJob = jobRepository.findById(jobId).orElseThrow();
        assertNotNull(savedJob);
        assertEquals(JobStatus.CREATED, savedJob.getStatus());
        assertEquals("INTEGRATION_TEST", savedJob.getType());

        // 4. Verify Kafka Event
        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, "job-events", Duration.ofSeconds(5));
        assertNotNull(record);
        
        JobEvent event = gson.fromJson(record.value(), JobEvent.class);
        assertNotNull(event.getMessageId());
        assertEquals(jobId, event.getJobId());
        assertEquals("INTEGRATION_TEST", event.getType());
    }
}
