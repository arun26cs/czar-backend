package com.czar.notes.integration;

import com.czar.notes.dto.NoteCreateRequest;
import com.czar.notes.dto.NoteResponse;
import com.czar.notes.dto.NoteUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for czar-notes against a real PostgreSQL container.
 *
 * Covers:
 *  - Note CRUD round-trip (create → get → update → soft-delete)
 *  - Title-based search (LIKE query via NoteRepository.searchByTitle)
 *  - Pin / unpin a note
 *  - RFC 7807 Problem Detail on 404
 *
 * Security: jwt.public-key-path is absent in test yml → SecurityConfig permits all.
 * Principal is injected via MockMvc's .principal() for controller auth extraction.
 *
 * Schema: The test-only Flyway migration in src/test/resources/db/migration creates
 * the users schema (users_profile, notes, note_tags, tags) with the tsvector trigger.
 * A test user row is inserted via JdbcTemplate before all tests since notes.user_id
 * has a FK constraint to users_profile.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NoteIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("czardb")
            .withUsername("czar_notes_role")
            .withPassword("czar_notes_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Enable Flyway; picks up src/test/resources/db/migration/V1__users_schema.sql
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        // Flyway history stored in public schema (default); users schema created by migration
        registry.add("spring.flyway.default-schema", () -> "public");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        // GCP Pub/Sub is auto-excluded via test application.yml
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeAll
    void insertTestUser() {
        // notes.user_id has a FK to users_profile — insert the test user once.
        jdbc.update(
                "INSERT INTO users.users_profile (id, display_name) VALUES (?, ?) ON CONFLICT DO NOTHING",
                USER_ID, "Test User");
    }

    // ── Create + Get round-trip ───────────────────────────────────────────────

    @Test
    @Order(1)
    void createAndGetNote_roundTrip() throws Exception {
        var req = new NoteCreateRequest("Integration Test Note", "{}", false, List.of());

        String body = mvc.perform(post("/api/v1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req))
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Integration Test Note"))
                .andExpect(jsonPath("$.pinned").value(false))
                .andReturn().getResponse().getContentAsString();

        UUID noteId = UUID.fromString(mapper.readTree(body).get("id").asText());

        mvc.perform(get("/api/v1/notes/{id}", noteId)
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(noteId.toString()))
                .andExpect(jsonPath("$.title").value("Integration Test Note"));
    }

    // ── Delete → 404 on subsequent GET ───────────────────────────────────────

    @Test
    @Order(2)
    void deleteNote_thenGetReturns404() throws Exception {
        var req = new NoteCreateRequest("Temporary Note", "{}", false, List.of());

        String body = mvc.perform(post("/api/v1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req))
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID noteId = UUID.fromString(mapper.readTree(body).get("id").asText());

        mvc.perform(delete("/api/v1/notes/{id}", noteId)
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/notes/{id}", noteId)
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isNotFound())
                // RFC 7807 Problem Detail
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── Search by title keyword (LIKE query) ─────────────────────────────────

    @Test
    @Order(3)
    void searchByTitle_returnsMatchingNotes() throws Exception {
        var req = new NoteCreateRequest("Searchable Keyword Note", "{}", false, List.of());

        mvc.perform(post("/api/v1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req))
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/notes")
                        .param("search", "Searchable")
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Searchable Keyword Note')]").exists());
    }

    // ── Pin / unpin ───────────────────────────────────────────────────────────

    @Test
    @Order(4)
    void pinNote_appearsInPinnedList() throws Exception {
        var req = new NoteCreateRequest("Pin Me Note", "{}", false, List.of());

        String body = mvc.perform(post("/api/v1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req))
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID noteId = UUID.fromString(mapper.readTree(body).get("id").asText());

        // Pin the note via update
        var updateReq = new NoteUpdateRequest(null, null, true, null);
        mvc.perform(put("/api/v1/notes/{id}", noteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(updateReq))
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true));

        // Verify it appears in the pinned list
        mvc.perform(get("/api/v1/notes/pinned")
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + noteId + "')]").exists());
    }

    // ── Search returns empty for unknown keyword ──────────────────────────────

    @Test
    @Order(5)
    void searchByTitle_noMatch_returnsEmptyList() throws Exception {
        mvc.perform(get("/api/v1/notes")
                        .param("search", "xyzzy_no_such_note_ever")
                        .principal(() -> USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
