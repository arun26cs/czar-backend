package com.czar.planner.controller;

import com.czar.planner.dto.*;
import com.czar.planner.service.PlanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlanController.class)
@Import(com.czar.planner.config.SecurityConfig.class)
class PlanControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean PlanService planService;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalDate TODAY = LocalDate.of(2025, 6, 1);

    private PlanResponse sampleResponse() {
        return new PlanResponse(PLAN_ID, USER_ID, "Morning Run", "task",
                TODAY, 7, 0, 60, "pending", false, false, false,
                Collections.emptyList(), Instant.now(), Instant.now());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/plans?date=...
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void listByDate_returns200() throws Exception {
        when(planService.listByDate(USER_ID, TODAY)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/plans").param("date", "2025-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Morning Run"))
                .andExpect(jsonPath("$[0].planType").value("task"));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/plans/{id}
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void getById_returns200() throws Exception {
        when(planService.getById(USER_ID, PLAN_ID)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/plans/{id}", PLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PLAN_ID.toString()))
                .andExpect(jsonPath("$.title").value("Morning Run"));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/plans
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void create_returns201() throws Exception {
        PlanCreateRequest req = new PlanCreateRequest("Morning Run", "task", TODAY, 7, 0, 60, null);
        when(planService.create(eq(USER_ID), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Morning Run"));
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void create_returns400_whenTitleBlank() throws Exception {
        mockMvc.perform(post("/api/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"planType\":\"task\",\"scheduledDate\":\"2025-06-01\",\"hour\":7,\"minute\":0,\"durationMinutes\":30}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void create_returns400_whenHourOutOfRange() throws Exception {
        mockMvc.perform(post("/api/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Run\",\"planType\":\"task\",\"scheduledDate\":\"2025-06-01\",\"hour\":25,\"minute\":0,\"durationMinutes\":30}"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/plans/{id}
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void update_returns200() throws Exception {
        PlanUpdateRequest req = new PlanUpdateRequest("Evening Run", null, null, null, null, null, null);
        PlanResponse resp = new PlanResponse(PLAN_ID, USER_ID, "Evening Run", "task",
                TODAY, 18, 0, 60, "pending", false, false, false,
                Collections.emptyList(), Instant.now(), Instant.now());
        when(planService.update(eq(USER_ID), eq(PLAN_ID), any())).thenReturn(resp);

        mockMvc.perform(put("/api/v1/plans/{id}", PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Evening Run"));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/plans/{id}
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/plans/{id}", PLAN_ID))
                .andExpect(status().isNoContent());

        verify(planService).delete(USER_ID, PLAN_ID);
    }

    // -------------------------------------------------------------------------
    // PATCH /api/v1/plans/{id}/status
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void updateStatus_returns200() throws Exception {
        PlanResponse resp = new PlanResponse(PLAN_ID, USER_ID, "Morning Run", "task",
                TODAY, 7, 0, 60, "done", false, false, false,
                Collections.emptyList(), Instant.now(), Instant.now());
        when(planService.updateStatus(eq(USER_ID), eq(PLAN_ID), any())).thenReturn(resp);

        mockMvc.perform(patch("/api/v1/plans/{id}/status", PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done"));
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void updateStatus_returns400_whenInvalidStatus() throws Exception {
        mockMvc.perform(patch("/api/v1/plans/{id}/status", PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // PATCH /api/v1/plans/{id}/confirm
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void confirm_returns200() throws Exception {
        PlanResponse resp = new PlanResponse(PLAN_ID, USER_ID, "Morning Run", "task",
                TODAY, 7, 0, 60, "pending", true, false, false,
                Collections.emptyList(), Instant.now(), Instant.now());
        when(planService.confirm(USER_ID, PLAN_ID)).thenReturn(resp);

        mockMvc.perform(patch("/api/v1/plans/{id}/confirm", PLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(true));
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/plans/{id}/tags
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void replaceTags_returns200() throws Exception {
        UUID tagId = UUID.randomUUID();
        PlanResponse resp = new PlanResponse(PLAN_ID, USER_ID, "Morning Run", "task",
                TODAY, 7, 0, 60, "pending", false, false, false,
                List.of(tagId), Instant.now(), Instant.now());
        when(planService.replaceTags(eq(USER_ID), eq(PLAN_ID), any())).thenReturn(resp);

        mockMvc.perform(put("/api/v1/plans/{id}/tags", PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[\"" + tagId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagIds").isArray());
    }
}
