package com.czar.notes.repository;

import com.czar.notes.domain.NoteTag;
import com.czar.notes.domain.NoteTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NoteTagRepository extends JpaRepository<NoteTag, NoteTagId> {

    List<NoteTag> findByIdNoteId(UUID noteId);

    @Modifying
    @Query("DELETE FROM NoteTag nt WHERE nt.id.noteId = :noteId")
    void deleteByNoteId(@Param("noteId") UUID noteId);
}
