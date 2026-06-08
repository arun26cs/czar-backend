package com.czar.user.service;

import com.czar.common.exception.ConflictException;
import com.czar.common.exception.ResourceNotFoundException;
import com.czar.user.domain.Tag;
import com.czar.user.dto.TagCreateRequest;
import com.czar.user.dto.TagResponse;
import com.czar.user.dto.TagUpdateRequest;
import com.czar.user.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TagService {

    private static final List<String[]> DEFAULT_TAGS = List.of(
            new String[]{"Personal", "#6366F1"},
            new String[]{"Work",     "#F59E0B"},
            new String[]{"Health",   "#10B981"},
            new String[]{"Travel",   "#3B82F6"},
            new String[]{"Finance",  "#EF4444"}
    );

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /** Seed 4 default tags for a newly registered user. Idempotent. */
    @Transactional
    public void seedDefaultTags(UUID userId) {
        for (String[] entry : DEFAULT_TAGS) {
            String name = entry[0];
            if (!tagRepository.existsByUserIdAndName(userId, name)) {
                Tag tag = new Tag();
                tag.setUserId(userId);
                tag.setName(name);
                tag.setColorHex(entry[1]);
                tagRepository.save(tag);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<TagResponse> listTags(UUID userId) {
        List<Tag> tags = tagRepository.findByUserIdOrderByCreatedAtAsc(userId);
        Map<UUID, Long> noteCounts = buildNoteCountMap(userId);
        return tags.stream()
                .map(t -> toResponse(t, noteCounts.getOrDefault(t.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Transactional
    public TagResponse createTag(UUID userId, TagCreateRequest request) {
        if (tagRepository.existsByUserIdAndName(userId, request.name())) {
            throw ConflictException.of("Tag", "name", request.name());
        }
        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(request.name());
        if (request.colorHex() != null) {
            tag.setColorHex(request.colorHex());
        }
        return toResponse(tagRepository.save(tag), 0L);
    }

    @Transactional
    public TagResponse updateTag(UUID userId, UUID tagId, TagUpdateRequest request) {
        Tag tag = tagRepository.findByIdAndUserId(tagId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Tag", tagId.toString()));

        if (request.name() != null) {
            if (tagRepository.existsByUserIdAndNameAndIdNot(userId, request.name(), tagId)) {
                throw ConflictException.of("Tag", "name", request.name());
            }
            tag.setName(request.name());
        }
        if (request.colorHex() != null) {
            tag.setColorHex(request.colorHex());
        }
        Map<UUID, Long> noteCounts = buildNoteCountMap(userId);
        return toResponse(tagRepository.save(tag), noteCounts.getOrDefault(tagId, 0L));
    }

    @Transactional
    public void deleteTag(UUID userId, UUID tagId) {
        Tag tag = tagRepository.findByIdAndUserId(tagId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Tag", tagId.toString()));
        // note_tags rows are cleaned by ON DELETE CASCADE in DB
        tagRepository.delete(tag);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Map<UUID, Long> buildNoteCountMap(UUID userId) {
        return tagRepository.countNotesByTagForUser(userId).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (Long) row[1]));
    }

    private TagResponse toResponse(Tag t, long noteCount) {
        return new TagResponse(t.getId(), t.getName(), t.getColorHex(), noteCount, t.getCreatedAt());
    }
}
