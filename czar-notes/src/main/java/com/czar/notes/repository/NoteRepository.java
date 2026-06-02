package com.czar.notes.repository;

import com.czar.notes.domain.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    List<Note> findByUserIdAndDeletedAtIsNull(UUID userId);

    List<Note> findByUserIdAndPinnedTrueAndDeletedAtIsNull(UUID userId);

    @Query("SELECT n FROM Note n WHERE n.userId = :userId AND n.deletedAt IS NULL " +
           "AND LOWER(n.title) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Note> searchByTitle(@Param("userId") UUID userId, @Param("query") String query);
}
