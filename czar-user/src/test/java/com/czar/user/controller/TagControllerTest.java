package com.czar.user.controller;

import com.czar.user.dto.TagCreateRequest;
import com.czar.user.dto.TagResponse;
import com.czar.user.service.TagService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TagController.class)
@Import(com.czar.user.config.SecurityConfig.class)
class TagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TagService tagService;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TAG_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void listTags_returns200() throws Exception {
        TagResponse tr = new TagResponse(TAG_ID, "Work", "#F59E0B", 3L, Instant.now());
        when(tagService.listTags(USER_ID)).thenReturn(List.of(tr));

        mockMvc.perform(get("/api/v1/users/me/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Work"))
                .andExpect(jsonPath("$[0].noteCount").value(3));
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void createTag_returns201() throws Exception {
        TagCreateRequest req = new TagCreateRequest("Personal", "#6366F1");
        TagResponse tr = new TagResponse(TAG_ID, "Personal", "#6366F1", 0L, Instant.now());
        when(tagService.createTag(eq(USER_ID), any())).thenReturn(tr);

        mockMvc.perform(post("/api/v1/users/me/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Personal"));
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void createTag_returns400_whenNameBlank() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\", \"colorHex\":\"#6366F1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void deleteTag_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/tags/{id}", TAG_ID))
                .andExpect(status().isNoContent());

        verify(tagService).deleteTag(USER_ID, TAG_ID);
    }
}
