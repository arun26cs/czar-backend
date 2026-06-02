package com.czar.notes.service;

import com.czar.common.exception.ResourceNotFoundException;
import com.czar.notes.domain.Note;
import com.czar.notes.domain.NoteTag;
import com.czar.notes.domain.NoteTagId;
import com.czar.notes.dto.*;
import com.czar.notes.messaging.NoteEventPublisher;
import com.czar.notes.repository.NoteRepository;
import com.czar.notes.repository.NoteTagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class NoteService {

    private final NoteRepository noteRepository;
    private final NoteTagRepository noteTagRepository;
    private final NoteEventPublisher eventPublisher;

    public NoteService(
            NoteRepository noteRepository,
            NoteTagRepository noteTagRepository,
            NoteEventPublisher eventPublisher) {
        this.noteRepository = noteRepository;
        this.noteTagRepository = noteTagRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> listAll(UUID userId) {
        return noteRepository.findByUserIdAndDeletedAtIsNull(userId)
                .stream().map(n -> toResponse(n, getTagIds(n.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> listPinned(UUID userId) {
        return noteRepository.findByUserIdAndPinnedTrueAndDeletedAtIsNull(userId)
                .stream().map(n -> toResponse(n, getTagIds(n.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> search(UUID userId, String query) {
        return noteRepository.searchByTitle(userId, query)
                .stream().map(n -> toResponse(n, getTagIds(n.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public NoteResponse getById(UUID userId, UUID id) {
        Note note = findOwned(userId, id);
        return toResponse(note, getTagIds(id));
    }

    @Transactional
    public NoteResponse create(UUID userId, NoteCreateRequest request) {
        Note note = new Note();
        note.setUserId(userId);
        note.setTitle(request.title() != null ? request.title() : "");
        note.setBody(request.body() != null ? request.body() : "{}");
        note.setPinned(request.pinned());
        note = noteRepository.save(note);

        List<UUID> tagIds = request.tagIds() != null ? request.tagIds() : Collections.emptyList();
        saveTags(note.getId(), tagIds);

        eventPublisher.publish("note.created", userId.toString(), note.getId().toString());
        return toResponse(note, tagIds);
    }

    @Transactional
    public NoteResponse update(UUID userId, UUID id, NoteUpdateRequest request) {
        Note note = findOwned(userId, id);

        if (request.title() != null) note.setTitle(request.title());
        if (request.body() != null) note.setBody(request.body());
        if (request.pinned() != null) note.setPinned(request.pinned());
        note = noteRepository.save(note);

        List<UUID> tagIds;
        if (request.tagIds() != null) {
            noteTagRepository.deleteByNoteId(id);
            saveTags(id, request.tagIds());
            tagIds = request.tagIds();
        } else {
            tagIds = getTagIds(id);
        }

        eventPublisher.publish("note.updated", userId.toString(), note.getId().toString());
        return toResponse(note, tagIds);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Note note = findOwned(userId, id);
        note.setDeletedAt(Instant.now());
        noteRepository.save(note);
        eventPublisher.publish("note.deleted", userId.toString(), note.getId().toString());
    }

    @Transactional
    public NoteResponse replaceTags(UUID userId, UUID id, NoteTagsRequest request) {
        findOwned(userId, id);
        noteTagRepository.deleteByNoteId(id);
        saveTags(id, request.tagIds());
        Note note = noteRepository.findById(id).orElseThrow();
        return toResponse(note, request.tagIds());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Note findOwned(UUID userId, UUID id) {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Note", id.toString()));
        if (!note.getUserId().equals(userId) || note.getDeletedAt() != null) {
            throw ResourceNotFoundException.of("Note", id.toString());
        }
        return note;
    }

    private void saveTags(UUID noteId, List<UUID> tagIds) {
        for (UUID tagId : tagIds) {
            noteTagRepository.save(new NoteTag(new NoteTagId(noteId, tagId)));
        }
    }

    private List<UUID> getTagIds(UUID noteId) {
        return noteTagRepository.findByIdNoteId(noteId).stream()
                .map(nt -> nt.getId().getTagId())
                .toList();
    }

    private NoteResponse toResponse(Note n, List<UUID> tagIds) {
        return new NoteResponse(
                n.getId(), n.getUserId(), n.getTitle(), n.getBody(),
                n.isPinned(), tagIds, n.getCreatedAt(), n.getUpdatedAt()
        );
    }
}
