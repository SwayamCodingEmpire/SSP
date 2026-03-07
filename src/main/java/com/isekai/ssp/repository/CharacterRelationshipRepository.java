package com.isekai.ssp.repository;

import com.isekai.ssp.entities.CharacterRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface CharacterRelationshipRepository extends JpaRepository<CharacterRelationship, Long> {

    @Query("SELECT r FROM CharacterRelationship r WHERE r.character1.id = :characterId OR r.character2.id = :characterId")
    List<CharacterRelationship> findByCharacterId(Long characterId);

    /**
     * Batch fetch all relationships for a set of character IDs (direction-agnostic).
     * Used by GraphRAG traversal to expand seed characters to their 1-hop neighbors
     * in a single query instead of N individual calls.
     */
    @Query("SELECT r FROM CharacterRelationship r WHERE r.character1.id IN :ids OR r.character2.id IN :ids")
    List<CharacterRelationship> findByCharacterIds(@Param("ids") Set<Long> ids);

    @Query("SELECT r FROM CharacterRelationship r WHERE r.character1.project.id = :projectId")
    List<CharacterRelationship> findByProjectId(Long projectId);

    /**
     * Finds the relationship between two characters regardless of direction.
     * Used to check for an existing relationship before creating a new one.
     */
    @Query("SELECT r FROM CharacterRelationship r WHERE " +
           "(r.character1.id = :c1Id AND r.character2.id = :c2Id) OR " +
           "(r.character1.id = :c2Id AND r.character2.id = :c1Id)")
    java.util.Optional<CharacterRelationship> findByCharacterPair(Long c1Id, Long c2Id);
}
