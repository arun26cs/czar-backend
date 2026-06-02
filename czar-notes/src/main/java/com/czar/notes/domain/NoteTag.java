package com.czar.notes.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "note_tags", schema = "users")
public class NoteTag {

    @EmbeddedId
    private NoteTagId id;

    public NoteTag() {}

    public NoteTag(NoteTagId id) {
        this.id = id;
    }

    public NoteTagId getId() { return id; }
    public void setId(NoteTagId id) { this.id = id; }
}
