package com.example.job_service.job;

import com.example.job_service.exception.GlobalExceptionHandler;
import com.example.job_service.exception.JobNotFoundException;
import com.example.job_service.job.dto.CreateJobRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

@WebMvcTest(JobController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdvancedApiFailureTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobService jobService;
    
    @MockBean
    private JwtUtil jwtUtil;
    
    @MockBean
    private JwtFilter jwtFilter;

    @Autowired
    private ObjectMapper objectMapper;

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

        mockMvc.perform(get("/api/v1/jobs?page=1&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageable").exists());
    }
}
