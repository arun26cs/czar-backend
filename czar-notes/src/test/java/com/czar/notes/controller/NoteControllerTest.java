package com.czar.notes.controller;

import com.czar.notes.dto.*;
import com.czar.notes.service.NoteService;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NoteController.class)
@Import(com.czar.notes.config.SecurityConfig.class)
class NoteControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean NoteService noteService;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NOTE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private NoteResponse sampleResponse() {
        return new NoteResponse(NOTE_ID, USER_ID, "My Note", "{}", false,
                Collections.emptyList(), Instant.now(), Instant.now());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/notes
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void list_returns200_withNotes() throws Exception {
        when(noteService.listAll(USER_ID)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("My Note"));
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void list_withSearch_callsSearchMethod() throws Exception {
        when(noteService.search(USER_ID, "hello")).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/notes").param("search", "hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("My Note"));

        verify(noteService).search(USER_ID, "hello");
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void list_withBlankSearch_callsListAll() throws Exception {
        when(noteService.listAll(USER_ID)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/notes").param("search", "  "))
                .andExpect(status().isOk());

        verify(noteService).listAll(USER_ID);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/notes/pinned
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void listPinned_returns200() throws Exception {
        NoteResponse pinned = new NoteResponse(NOTE_ID, USER_ID, "Pinned", "{}", true,
                Collections.emptyList(), Instant.now(), Instant.now());
        when(noteService.listPinned(USER_ID)).thenReturn(List.of(pinned));

        mockMvc.perform(get("/api/v1/notes/pinned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pinned").value(true));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/notes/{id}
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void getById_returns200() throws Exception {
        when(noteService.getById(USER_ID, NOTE_ID)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/notes/{id}", NOTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(NOTE_ID.toString()))
                .andExpect(jsonPath("$.title").value("My Note"));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/notes
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void create_returns201() throws Exception {
        NoteCreateRequest req = new NoteCreateRequest("My Note", "{}", false, null);
        when(noteService.create(eq(USER_ID), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("My Note"));
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void create_withMinimalBody_returns201() throws Exception {
        when(noteService.create(eq(USER_ID), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/notes/{id}
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void update_returns200() throws Exception {
        NoteUpdateRequest req = new NoteUpdateRequest("Updated", null, null, null);
        NoteResponse resp = new NoteResponse(NOTE_ID, USER_ID, "Updated", "{}", false,
                Collections.emptyList(), Instant.now(), Instant.now());
        when(noteService.update(eq(USER_ID), eq(NOTE_ID), any())).thenReturn(resp);

        mockMvc.perform(put("/api/v1/notes/{id}", NOTE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/notes/{id}
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/notes/{id}", NOTE_ID))
                .andExpect(status().isNoContent());

        verify(noteService).delete(USER_ID, NOTE_ID);
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/notes/{id}/tags
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void replaceTags_returns200() throws Exception {
        UUID tagId = UUID.randomUUID();
        NoteResponse resp = new NoteResponse(NOTE_ID, USER_ID, "My Note", "{}", false,
                List.of(tagId), Instant.now(), Instant.now());
        when(noteService.replaceTags(eq(USER_ID), eq(NOTE_ID), any())).thenReturn(resp);

        mockMvc.perform(put("/api/v1/notes/{id}/tags", NOTE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[\"" + tagId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagIds").isArray());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void replaceTags_returns400_whenTagIdsNull() throws Exception {
        mockMvc.perform(put("/api/v1/notes/{id}/tags", NOTE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":null}"))
                .andExpect(status().isBadRequest());
    }
}
