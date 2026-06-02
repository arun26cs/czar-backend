package com.czar.user.controller;

import com.czar.user.dto.TagCreateRequest;
import com.czar.user.dto.TagResponse;
import com.czar.user.dto.TagUpdateRequest;
import com.czar.user.service.TagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public ResponseEntity<List<TagResponse>> listTags(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(tagService.listTags(userId));
    }

    @PostMapping
    public ResponseEntity<TagResponse> createTag(
            Authentication auth,
            @Valid @RequestBody TagCreateRequest request) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.createTag(userId, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TagResponse> updateTag(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody TagUpdateRequest request) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(tagService.updateTag(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(
            Authentication auth,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(auth.getName());
        tagService.deleteTag(userId, id);
        return ResponseEntity.noContent().build();
    }
}
