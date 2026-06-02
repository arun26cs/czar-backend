package com.czar.user.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Junction entity for users.note_tags.
 * Used only for note_count projection queries — notes service owns note writes.
 */
@Entity
@Table(name = "note_tags", schema = "users")
public class NoteTag {

    @EmbeddedId
    private NoteTagId id;

    public NoteTagId getId() { return id; }
    public void setId(NoteTagId id) { this.id = id; }

    @Embeddable
    public static class NoteTagId implements java.io.Serializable {
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
        public UUID getTagId() { return tagId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NoteTagId other)) return false;
            return java.util.Objects.equals(noteId, other.noteId)
                    && java.util.Objects.equals(tagId, other.tagId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(noteId, tagId);
        }
    }
}
