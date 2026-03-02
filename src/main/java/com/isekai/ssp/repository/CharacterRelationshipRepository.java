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
}
