package com.czar.notes.dto;

import java.util.List;
import java.util.UUID;

public record NoteUpdateRequest(
        String title,
        String body,
        Boolean pinned,
        List<UUID> tagIds
) {}
