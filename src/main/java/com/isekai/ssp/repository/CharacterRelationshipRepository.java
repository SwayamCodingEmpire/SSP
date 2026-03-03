package com.isekai.ssp.repository;

import com.isekai.ssp.entities.CharacterRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CharacterRelationshipRepository extends JpaRepository<CharacterRelationship, Long> {

    @Query("SELECT r FROM CharacterRelationship r WHERE r.character1.id = :characterId OR r.character2.id = :characterId")
    List<CharacterRelationship> findByCharacterId(Long characterId);

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
