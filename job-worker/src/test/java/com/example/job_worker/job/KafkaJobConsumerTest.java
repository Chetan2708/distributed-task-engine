package com.example.job_worker.job;

import com.example.job_worker.job.JobStatus;
import com.example.job_worker.job.dto.JobEvent;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaJobConsumerTest {

    @Mock
    private JobRepository jobRepository;

    @Spy
    private Gson gson = new Gson();

    @InjectMocks
    private KafkaJobConsumer kafkaJobConsumer;

    @Test
    void consume_processesJobSuccessfully() throws InterruptedException {
        // Arrange
        UUID jobId = UUID.randomUUID();
        JobEvent event = new JobEvent(jobId, "TEST");
        String message = gson.toJson(event);

        JobEntity job = new JobEntity();
        job.setId(jobId);
        job.setStatus(JobStatus.CREATED);
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        kafkaJobConsumer.consume(message);

        // Assert
        verify(jobRepository, times(1)).findById(jobId);
        verify(jobRepository, times(3)).save(job);
        
        // Final state should be SUCCESS
        assertEquals(JobStatus.SUCCESS, job.getStatus());
    }
}
