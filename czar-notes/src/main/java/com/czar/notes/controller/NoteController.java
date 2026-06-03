package com.czar.notes.controller;

import com.czar.notes.dto.*;
import com.czar.notes.service.NoteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public ResponseEntity<List<NoteResponse>> list(
            Authentication auth,
            @RequestParam(required = false) String search) {
        UUID userId = UUID.fromString(auth.getName());
        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(noteService.search(userId, search));
        }
        return ResponseEntity.ok(noteService.listAll(userId));
    }

    @GetMapping("/pinned")
    public ResponseEntity<List<NoteResponse>> listPinned(Authentication auth) {
        return ResponseEntity.ok(noteService.listPinned(UUID.fromString(auth.getName())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteResponse> getById(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(noteService.getById(UUID.fromString(auth.getName()), id));
    }

    @PostMapping
    public ResponseEntity<NoteResponse> create(
            Authentication auth,
            @Valid @RequestBody NoteCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(noteService.create(UUID.fromString(auth.getName()), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NoteResponse> update(
            Authentication auth,
            @PathVariable UUID id,
            @RequestBody NoteUpdateRequest request) {
        return ResponseEntity.ok(noteService.update(UUID.fromString(auth.getName()), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID id) {
        noteService.delete(UUID.fromString(auth.getName()), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/tags")
    public ResponseEntity<NoteResponse> replaceTags(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody NoteTagsRequest request) {
        return ResponseEntity.ok(noteService.replaceTags(UUID.fromString(auth.getName()), id, request));
    }
}
