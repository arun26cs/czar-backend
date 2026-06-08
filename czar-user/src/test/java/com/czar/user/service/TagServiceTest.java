package com.czar.user.service;

import com.czar.common.exception.ConflictException;
import com.czar.common.exception.ResourceNotFoundException;
import com.czar.user.domain.Tag;
import com.czar.user.dto.TagCreateRequest;
import com.czar.user.dto.TagResponse;
import com.czar.user.dto.TagUpdateRequest;
import com.czar.user.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TagService tagService;

    private UUID userId;
    private UUID tagId;
    private Tag sampleTag;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tagId = UUID.randomUUID();

        sampleTag = new Tag();
        sampleTag.setUserId(userId);
        sampleTag.setName("Work");
        sampleTag.setColorHex("#F59E0B");
    }

    @Test
    void seedDefaultTags_createsAllFiveTags() {
        when(tagRepository.existsByUserIdAndName(any(), anyString())).thenReturn(false);
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        tagService.seedDefaultTags(userId);

        verify(tagRepository, times(5)).save(any(Tag.class));
    }

    @Test
    void seedDefaultTags_isIdempotent_skipsExisting() {
        // Register anyString() first, then specific — Mockito matches last-registered first
        when(tagRepository.existsByUserIdAndName(eq(userId), anyString())).thenReturn(false);
        when(tagRepository.existsByUserIdAndName(eq(userId), eq("Personal"))).thenReturn(true);
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        tagService.seedDefaultTags(userId);

        verify(tagRepository, times(4)).save(any(Tag.class)); // 5 - 1 existing
    }

    @Test
    void createTag_success() {
        when(tagRepository.existsByUserIdAndName(userId, "Work")).thenReturn(false);
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> {
            Tag t = inv.getArgument(0);
            return t;
        });

        TagCreateRequest req = new TagCreateRequest("Work", "#F59E0B");
        TagResponse response = tagService.createTag(userId, req);

        assertThat(response.name()).isEqualTo("Work");
        assertThat(response.colorHex()).isEqualTo("#F59E0B");
        assertThat(response.noteCount()).isZero();
    }

    @Test
    void createTag_throwsConflict_whenNameExists() {
        when(tagRepository.existsByUserIdAndName(userId, "Work")).thenReturn(true);

        assertThatThrownBy(() -> tagService.createTag(userId, new TagCreateRequest("Work", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Work");
    }

    @Test
    void updateTag_rename_success() {
        when(tagRepository.findByIdAndUserId(tagId, userId)).thenReturn(Optional.of(sampleTag));
        when(tagRepository.existsByUserIdAndNameAndIdNot(userId, "Health", tagId)).thenReturn(false);
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tagRepository.countNotesByTagForUser(userId)).thenReturn(Collections.emptyList());

        TagResponse response = tagService.updateTag(userId, tagId, new TagUpdateRequest("Health", null));

        assertThat(response.name()).isEqualTo("Health");
        assertThat(response.colorHex()).isEqualTo("#F59E0B"); // unchanged
    }

    @Test
    void updateTag_throwsConflict_whenNewNameExistsForUser() {
        when(tagRepository.findByIdAndUserId(tagId, userId)).thenReturn(Optional.of(sampleTag));
        when(tagRepository.existsByUserIdAndNameAndIdNot(userId, "Personal", tagId)).thenReturn(true);

        assertThatThrownBy(() -> tagService.updateTag(userId, tagId, new TagUpdateRequest("Personal", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteTag_success() {
        when(tagRepository.findByIdAndUserId(tagId, userId)).thenReturn(Optional.of(sampleTag));

        tagService.deleteTag(userId, tagId);

        verify(tagRepository).delete(sampleTag);
    }

    @Test
    void deleteTag_throwsNotFound_whenTagNotOwnedByUser() {
        when(tagRepository.findByIdAndUserId(tagId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.deleteTag(userId, tagId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listTags_returnsMappedResponses() {
        Tag t1 = new Tag();
        t1.setUserId(userId);
        t1.setName("Personal");
        t1.setColorHex("#6366F1");

        when(tagRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(t1));
        when(tagRepository.countNotesByTagForUser(userId)).thenReturn(Collections.emptyList());

        List<TagResponse> result = tagService.listTags(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Personal");
        assertThat(result.get(0).noteCount()).isZero();
    }
}
