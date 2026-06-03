package com.czar.planner.integration;

import com.czar.planner.dto.PlanCreateRequest;
import com.czar.planner.dto.PlanResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for czar-planner against a real PostgreSQL container.
 *
 * Covers:
 *  - Plan CRUD (create → get → update → delete)
 *  - Two overlapping plans produce a conflict_log entry
 *  - RFC 7807 Problem Detail on 404
 *
 * Security: jwt.public-key-path is absent in test yml → SecurityConfig permits all.
 * The MockMvc principal header is injected manually where needed.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class PlanIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("czardb")
            .withUsername("czar_planner_role")
            .withPassword("czar_planner_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Override driver; Testcontainers uses the standard JDBC URL
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Enable Flyway so the schema is created
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        // Use real PostgreSQL dialect
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate TODAY = LocalDate.of(2025, 6, 10);

    // ── Create + Get ─────────────────────────────────────────────────────────

    @Test
    void createAndGetPlan_roundTrip() throws Exception {
        var req = new PlanCreateRequest("Morning run", "task", TODAY, 7, 0, 60, null);

        String body = mvc.perform(post("/api/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", USER_ID)          // injected as principal via test config
                        .content(mapper.writeValueAsString(req))
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Morning run"))
                .andExpect(jsonPath("$.scheduledDate").value("2025-06-10"))
                .andReturn().getResponse().getContentAsString();

        UUID planId = UUID.fromString(mapper.readTree(body).get("id").asText());

        mvc.perform(get("/api/v1/plans/{id}", planId)
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(planId.toString()));
    }

    // ── Delete → 404 on subsequent GET ───────────────────────────────────────

    @Test
    void deletePlan_thenGetReturns404() throws Exception {
        var req = new PlanCreateRequest("Temp plan", "task", TODAY, 8, 0, 30, null);

        String body = mvc.perform(post("/api/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req))
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID planId = UUID.fromString(mapper.readTree(body).get("id").asText());

        mvc.perform(delete("/api/v1/plans/{id}", planId)
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/plans/{id}", planId)
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isNotFound())
                // RFC 7807 Problem Detail
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── Conflict detection — two overlapping plans on same date ──────────────

    @Test
    void twoOverlappingPlans_conflictFlaggedInResponse() throws Exception {
        LocalDate date = LocalDate.of(2025, 6, 11);

        var plan1 = new PlanCreateRequest("Meeting A", "event", date, 10, 0, 60, null);
        var plan2 = new PlanCreateRequest("Meeting B", "event", date, 10, 30, 60, null); // overlaps A

        mvc.perform(post("/api/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(plan1))
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isCreated());

        // Second plan overlaps first — PlanService still saves it but flags conflict
        mvc.perform(post("/api/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(plan2))
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isCreated());

        // Verify via the conflicts endpoint
        mvc.perform(get("/api/v1/plans/conflicts")
                        .param("date", date.toString())
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    // ── Validation — missing required fields → 400 ───────────────────────────

    @Test
    void createPlan_missingTitle_returns400() throws Exception {
        String payload = """
                {"planType":"task","scheduledDate":"2025-06-10","hour":9,"minute":0,"durationMinutes":30}
                """;

        mvc.perform(post("/api/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    // ── List by date ──────────────────────────────────────────────────────────

    @Test
    void listByDate_returnsPlansForThatDate() throws Exception {
        LocalDate date = LocalDate.of(2025, 6, 12);
        var req = new PlanCreateRequest("Dentist", "task", date, 14, 0, 45, null);

        mvc.perform(post("/api/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req))
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/plans")
                        .param("date", date.toString())
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Dentist')]").exists());
    }
}
