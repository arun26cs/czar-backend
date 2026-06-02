package com.czar.notes.service;

import com.czar.common.exception.ResourceNotFoundException;
import com.czar.notes.domain.Note;
import com.czar.notes.domain.NoteTag;
import com.czar.notes.dto.*;
import com.czar.notes.messaging.NoteEventPublisher;
import com.czar.notes.repository.NoteRepository;
import com.czar.notes.repository.NoteTagRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock NoteRepository noteRepository;
    @Mock NoteTagRepository noteTagRepository;
    @Mock NoteEventPublisher eventPublisher;

    @InjectMocks
    NoteService noteService;

    private UUID userId;
    private UUID noteId;
    private Note sampleNote;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        noteId = UUID.randomUUID();

        sampleNote = new Note();
        sampleNote.setId(noteId);
        sampleNote.setUserId(userId);
        sampleNote.setTitle("My Note");
        sampleNote.setBody("{\"text\":\"hello\"}");
        sampleNote.setPinned(false);
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Test
    void create_savesAndPublishesEvent() {
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            if (n.getId() == null) n.setId(UUID.randomUUID());
            return n;
        });

        NoteCreateRequest req = new NoteCreateRequest("My Note", "{}", false, Collections.emptyList());
        NoteResponse resp = noteService.create(userId, req);

        assertThat(resp.title()).isEqualTo("My Note");
        verify(noteRepository).save(any(Note.class));
        verify(eventPublisher).publish(eq("note.created"), eq(userId.toString()), any());
    }

    @Test
    void create_withTags_savesTags() {
        UUID tagId = UUID.randomUUID();
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            if (n.getId() == null) n.setId(UUID.randomUUID());
            return n;
        });

        NoteCreateRequest req = new NoteCreateRequest("Tagged", "{}", false, List.of(tagId));
        noteService.create(userId, req);

        verify(noteTagRepository).save(any(NoteTag.class));
    }

    @Test
    void create_withNullTitle_defaultsToEmpty() {
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            if (n.getId() == null) n.setId(UUID.randomUUID());
            return n;
        });

        NoteCreateRequest req = new NoteCreateRequest(null, null, false, null);
        NoteResponse resp = noteService.create(userId, req);

        assertThat(resp.title()).isEqualTo("");
        assertThat(resp.body()).isEqualTo("{}");
    }

    // -------------------------------------------------------------------------
    // getById
    // -------------------------------------------------------------------------

    @Test
    void getById_returnsResponse() {
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(sampleNote));
        when(noteTagRepository.findByIdNoteId(noteId)).thenReturn(Collections.emptyList());

        NoteResponse resp = noteService.getById(userId, noteId);

        assertThat(resp.title()).isEqualTo("My Note");
    }

    @Test
    void getById_throwsNotFound_whenMissing() {
        when(noteRepository.findById(noteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.getById(userId, noteId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_throwsNotFound_whenWrongOwner() {
        Note other = new Note();
        other.setUserId(UUID.randomUUID());
        other.setTitle("Alien");
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> noteService.getById(userId, noteId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_throwsNotFound_whenSoftDeleted() {
        sampleNote.setDeletedAt(Instant.now());
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(sampleNote));

        assertThatThrownBy(() -> noteService.getById(userId, noteId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // listAll
    // -------------------------------------------------------------------------

    @Test
    void listAll_returnsOnlyActiveNotes() {
        when(noteRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(List.of(sampleNote));
        when(noteTagRepository.findByIdNoteId(any())).thenReturn(Collections.emptyList());

        List<NoteResponse> result = noteService.listAll(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("My Note");
    }

    // -------------------------------------------------------------------------
    // listPinned
    // -------------------------------------------------------------------------

    @Test
    void listPinned_returnsOnlyPinnedNotes() {
        sampleNote.setPinned(true);
        when(noteRepository.findByUserIdAndPinnedTrueAndDeletedAtIsNull(userId)).thenReturn(List.of(sampleNote));
        when(noteTagRepository.findByIdNoteId(any())).thenReturn(Collections.emptyList());

        List<NoteResponse> result = noteService.listPinned(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).pinned()).isTrue();
    }

    // -------------------------------------------------------------------------
    // search
    // -------------------------------------------------------------------------

    @Test
    void search_delegatesToRepository() {
        when(noteRepository.searchByTitle(userId, "hello")).thenReturn(List.of(sampleNote));
        when(noteTagRepository.findByIdNoteId(any())).thenReturn(Collections.emptyList());

        List<NoteResponse> result = noteService.search(userId, "hello");

        assertThat(result).hasSize(1);
        verify(noteRepository).searchByTitle(userId, "hello");
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void update_changesTitle() {
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(sampleNote));
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));
        when(noteTagRepository.findByIdNoteId(any())).thenReturn(Collections.emptyList());

        NoteResponse resp = noteService.update(userId, noteId,
                new NoteUpdateRequest("Updated Title", null, null, null));

        assertThat(resp.title()).isEqualTo("Updated Title");
        verify(eventPublisher).publish(eq("note.updated"), any(), any());
    }

    @Test
    void update_withTagIds_replacesOldTags() {
        UUID tagId = UUID.randomUUID();
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(sampleNote));
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        noteService.update(userId, noteId, new NoteUpdateRequest(null, null, null, List.of(tagId)));

        verify(noteTagRepository).deleteByNoteId(noteId);
        verify(noteTagRepository).save(any(NoteTag.class));
    }

    @Test
    void update_pin_setsTrue() {
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(sampleNote));
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));
        when(noteTagRepository.findByIdNoteId(any())).thenReturn(Collections.emptyList());

        NoteResponse resp = noteService.update(userId, noteId,
                new NoteUpdateRequest(null, null, true, null));

        assertThat(resp.pinned()).isTrue();
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_softDeletes() {
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(sampleNote));
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        noteService.delete(userId, noteId);

        assertThat(sampleNote.getDeletedAt()).isNotNull();
        verify(eventPublisher).publish(eq("note.deleted"), any(), any());
    }

    // -------------------------------------------------------------------------
    // replaceTags
    // -------------------------------------------------------------------------

    @Test
    void replaceTags_deletesOldAndSavesNew() {
        UUID tagId = UUID.randomUUID();
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(sampleNote));

        noteService.replaceTags(userId, noteId, new NoteTagsRequest(List.of(tagId)));

        verify(noteTagRepository).deleteByNoteId(noteId);
        verify(noteTagRepository).save(any(NoteTag.class));
    }
}
