package com.isekai.ssp.repository;

import com.isekai.ssp.entities.CharacterState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CharacterStateRepository extends JpaRepository<CharacterState, Long> {

    /** All state snapshots for a character, ordered chronologically */
    List<CharacterState> findByCharacterIdOrderByChapterNumberAsc(Long characterId);

    /**
     * Most recent state snapshot for a character at or before a given chapter.
     * Used during flashback retrieval: "who was this character at chapter N?"
     */
    @Query("""
            SELECT cs FROM CharacterState cs
            WHERE cs.character.id = :characterId
              AND cs.chapterNumber <= :chapterNumber
            ORDER BY cs.chapterNumber DESC
            LIMIT 1
            """)
    Optional<CharacterState> findLatestStateAtOrBefore(Long characterId, int chapterNumber);

    /** All states for all characters in a project, at or before a chapter number */
    @Query("""
            SELECT cs FROM CharacterState cs
            WHERE cs.character.project.id = :projectId
              AND cs.chapterNumber <= :chapterNumber
            ORDER BY cs.character.id ASC, cs.chapterNumber DESC
            """)
    List<CharacterState> findProjectStatesAtOrBefore(Long projectId, int chapterNumber);

    List<CharacterState> findByChapterId(Long chapterId);
}