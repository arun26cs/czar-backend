package com.czar.planner.controller;

import com.czar.planner.dto.ConflictPairResponse;
import com.czar.planner.dto.PlanStatsResponse;
import com.czar.planner.service.PlanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConflictController.class)
@Import(com.czar.planner.config.SecurityConfig.class)
class ConflictControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean PlanService planService;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_A  = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PLAN_B  = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final LocalDate TODAY = LocalDate.of(2025, 6, 1);

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void getConflicts_returns200_withList() throws Exception {
        ConflictPairResponse pair = new ConflictPairResponse(
                UUID.randomUUID(), PLAN_A, "Morning Run", PLAN_B, "Breakfast", Instant.now());
        when(planService.getConflicts(USER_ID)).thenReturn(List.of(pair));

        mockMvc.perform(get("/api/v1/plans/conflicts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].planATitle").value("Morning Run"))
                .andExpect(jsonPath("$[0].planBTitle").value("Breakfast"));
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void getConflicts_returns200_whenEmpty() throws Exception {
        when(planService.getConflicts(USER_ID)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/plans/conflicts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void getStats_returns200_withCounts() throws Exception {
        PlanStatsResponse stats = new PlanStatsResponse(5, 2, 2, 1, 0);
        when(planService.getStats(eq(USER_ID), eq(TODAY))).thenReturn(stats);

        mockMvc.perform(get("/api/v1/plans/stats").param("date", "2025-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.done").value(2))
                .andExpect(jsonPath("$.pending").value(2))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.unresolvedConflicts").value(0));
    }
}
