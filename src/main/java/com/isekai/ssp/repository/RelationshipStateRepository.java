package com.isekai.ssp.repository;

import com.isekai.ssp.entities.RelationshipState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RelationshipStateRepository extends JpaRepository<RelationshipState, Long> {

    List<RelationshipState> findByRelationshipId(Long relationshipId);

    /**
     * Returns the most recent snapshot of a relationship at or before a given chapter.
     * Used for flashback-aware context: when translating a ch.5 flashback, retrieve
     * the relationship state AS OF chapter 5, not the current state.
     */
    @Query("SELECT rs FROM RelationshipState rs WHERE rs.relationship.id = :relationshipId " +
           "AND rs.chapterNumber <= :chapterNumber ORDER BY rs.chapterNumber DESC")
    List<RelationshipState> findAtOrBeforeChapter(Long relationshipId, int chapterNumber);

    default Optional<RelationshipState> findLatestAtOrBefore(Long relationshipId, int chapterNumber) {
        return findAtOrBeforeChapter(relationshipId, chapterNumber).stream().findFirst();
    }
}
