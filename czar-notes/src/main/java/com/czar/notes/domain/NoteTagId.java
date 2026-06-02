package com.czar.notes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class NoteTagId implements Serializable {

    @Column(name = "note_id")
    private UUID noteId;

    @Column(name = "tag_id")
    private UUID tagId;

    public NoteTagId() {}

    public NoteTagId(UUID noteId, UUID tagId) {
        this.noteId = noteId;
        this.tagId = tagId;
    }

    public UUID getNoteId() { return noteId; }
    public void setNoteId(UUID noteId) { this.noteId = noteId; }

    public UUID getTagId() { return tagId; }
    public void setTagId(UUID tagId) { this.tagId = tagId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NoteTagId)) return false;
        NoteTagId that = (NoteTagId) o;
        return Objects.equals(noteId, that.noteId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(noteId, tagId);
    }
}
