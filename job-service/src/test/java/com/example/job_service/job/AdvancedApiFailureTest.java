package com.example.job_service.job;

import com.example.job_service.exception.GlobalExceptionHandler;
import com.example.job_service.exception.JobNotFoundException;
import com.example.job_service.job.dto.CreateJobRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import com.example.job_service.config.SecurityConfig;
import com.example.job_service.config.JwtUtil;
import com.example.job_service.config.JwtFilter;

@ExtendWith(MockitoExtension.class)
class AdvancedApiFailureTest {

    private MockMvc mockMvc;

    @Mock
    private JobService jobService;

    @InjectMocks
    private JobController jobController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(jobController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void shouldReturn400OnInvalidRequestPayload() throws Exception {
        CreateJobRequest request = new CreateJobRequest();
        // Missing required type and payload

        mockMvc.perform(post("/api/v1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturn404OnJobNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.getJob(id)).thenThrow(new JobNotFoundException(id));

        mockMvc.perform(get("/api/v1/jobs/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void shouldSupportPagination() throws Exception {
        when(jobService.getAllJobs(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        ResponseEntity<Page<JobEntity>> response = jobController.getAllJobs(PageRequest.of(1, 5));
        
        org.junit.jupiter.api.Assertions.assertEquals(200, response.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertNotNull(response.getBody());
    }
}
