package com.czar.notes.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record NoteTagsRequest(
        @NotNull List<UUID> tagIds
) {}
