package com.isekai.ssp.repository;

import com.isekai.ssp.entities.CharacterPersonality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CharacterPersonalityRepository extends JpaRepository<CharacterPersonality, Long> {

    List<CharacterPersonality> findByCharacterId(Long characterId);

    Optional<CharacterPersonality> findByCharacterIdAndName(Long characterId, String name);

    Optional<CharacterPersonality> findByCharacterIdAndIsPrimaryTrue(Long characterId);
}
