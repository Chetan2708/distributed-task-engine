package com.example.job_service.job;

import com.example.job_service.config.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.flyway.enabled=false",
    "outbox.poll.delay=500",
    "jobflow.jwt.secret=test-secret-key-that-is-at-least-256-bits-long",
    "jobflow.auth.user.username=user",
    "jobflow.auth.user.password=password",
    "jobflow.auth.admin.username=admin",
    "jobflow.auth.admin.password=admin"
})
@EmbeddedKafka(partitions = 1, topics = { "job-events" })
public class JobStatsSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JobService jobService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(username = "regular_user", roles = {"USER"})
    void getStats_whenAuthenticatedAsUser_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/stats"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    void getStats_whenAuthenticatedAsAdmin_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").exists());
    }

    @Test
    void getStats_withUserJwtToken_returnsForbidden() throws Exception {
        String userToken = jwtUtil.generateToken("user", "ROLE_USER");

        mockMvc.perform(get("/api/v1/jobs/stats")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStats_withAdminJwtToken_returnsOk() throws Exception {
        String adminToken = jwtUtil.generateToken("admin", "ROLE_ADMIN,ROLE_USER");

        mockMvc.perform(get("/api/v1/jobs/stats")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").exists());
    }

    @Test
    void getStats_unauthenticated_returnsUnauthorizedOrForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/stats"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void jobServiceGetStats_whenUserRole_throwsAccessDeniedException() {
        UsernamePasswordAuthenticationToken userAuth = new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(userAuth);

        try {
            assertThrows(AccessDeniedException.class, () -> jobService.getStats());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void jobServiceGetStats_whenAdminRole_succeeds() {
        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        try {
            Map<String, Long> stats = jobService.getStats();
            assertNotNull(stats);
            assertNotNull(stats.get("total"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
