package com.czar.user.repository;

import com.czar.user.domain.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Tag> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndName(UUID userId, String name);

    boolean existsByUserIdAndNameAndIdNot(UUID userId, String name, UUID id);

    /**
     * Count note_tags rows per tag for a given user — used for noteCount in GET /tags.
     * Returns [tagId, count] pairs; tags with zero notes are omitted (use LEFT JOIN in service).
     */
    @Query("""
            SELECT nt.id.tagId, COUNT(nt.id.noteId)
            FROM NoteTag nt
            WHERE nt.id.tagId IN (SELECT t.id FROM Tag t WHERE t.userId = :userId)
            GROUP BY nt.id.tagId
            """)
    List<Object[]> countNotesByTagForUser(@Param("userId") UUID userId);
}
