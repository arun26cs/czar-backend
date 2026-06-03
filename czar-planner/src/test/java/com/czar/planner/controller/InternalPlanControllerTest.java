package com.czar.planner.controller;

import com.czar.planner.dto.PlanResponse;
import com.czar.planner.service.PlanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalPlanController.class)
@Import(com.czar.planner.config.SecurityConfig.class)
@TestPropertySource(properties = "czar.internal.service-token=test-token")
class InternalPlanControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PlanService planService;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalDate DATE = LocalDate.of(2025, 6, 1);

    private PlanResponse samplePlan() {
        return new PlanResponse(PLAN_ID, USER_ID, "Standup", "event",
                DATE, 9, 0, 30, "pending", false, false, false,
                List.of(), Instant.now(), Instant.now());
    }

    @Test
    void validToken_returnsPlans() throws Exception {
        when(planService.listByDate(any(), any())).thenReturn(List.of(samplePlan()));

        mockMvc.perform(get("/internal/v1/plans")
                        .header("X-Service-Token", "test-token")
                        .param("userId", USER_ID.toString())
                        .param("date", "2025-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Standup"))
                .andExpect(jsonPath("$[0].hour").value(9));
    }

    @Test
    void invalidToken_returns403() throws Exception {
        mockMvc.perform(get("/internal/v1/plans")
                        .header("X-Service-Token", "wrong-token")
                        .param("userId", USER_ID.toString())
                        .param("date", "2025-06-01"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingToken_returns400() throws Exception {
        mockMvc.perform(get("/internal/v1/plans")
                        .param("userId", USER_ID.toString())
                        .param("date", "2025-06-01"))
                .andExpect(status().is4xxClientError());
    }
}
