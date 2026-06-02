package com.czar.notes.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NoteResponse(
        UUID id,
        UUID userId,
        String title,
        String body,
        boolean pinned,
        List<UUID> tagIds,
        Instant createdAt,
        Instant updatedAt
) {}
