package com.czar.user.repository;

import com.czar.user.domain.NoteTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface NoteTagRepository extends JpaRepository<NoteTag, NoteTag.NoteTagId> {

    @Modifying
    @Query("DELETE FROM NoteTag nt WHERE nt.id.tagId = :tagId")
    void deleteByTagId(@Param("tagId") UUID tagId);
}
