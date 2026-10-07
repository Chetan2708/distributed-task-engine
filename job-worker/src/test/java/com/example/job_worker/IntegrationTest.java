package com.example.job_worker;

import com.example.job_worker.job.JobEntity;
import com.example.job_worker.job.JobRepository;
import com.example.job_worker.job.JobStatus;
import com.example.job_worker.job.dto.JobEvent;
import com.google.gson.Gson;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.kafka.consumer.group-id=test-worker-group",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
@EmbeddedKafka(partitions = 1, topics = { "job-events" })
@DirtiesContext
public class IntegrationTest {

    @Autowired
    private JobRepository jobRepository;

    private Gson gson = new Gson();

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean
    private RLock lock;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();

        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);

        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void testWorkerProcessing_updatesJobToSuccess() {
        // 1. Prepare Database State
        JobEntity job = new JobEntity();
        job.setId(UUID.randomUUID());
        job.setStatus(JobStatus.CREATED);
        job.setType("TEST");
        jobRepository.save(job);

        // 2. Publish Kafka Event
        JobEvent event = new JobEvent(UUID.randomUUID(), job.getId(), "TEST");
        kafkaTemplate.send("job-events", gson.toJson(event));

        // 3. Await and Verify Database Update (Worker takes 3 seconds to process)
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            JobEntity updatedJob = jobRepository.findById(job.getId()).orElseThrow();
            assertThat(updatedJob.getStatus()).isEqualTo(JobStatus.SUCCESS);
            assertThat(updatedJob.getCompletedAt()).isNotNull();
        });
    }
}
