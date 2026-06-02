package com.czar.notes.dto;

import java.util.List;
import java.util.UUID;

public record NoteCreateRequest(
        String title,
        String body,
        boolean pinned,
        List<UUID> tagIds
) {}
