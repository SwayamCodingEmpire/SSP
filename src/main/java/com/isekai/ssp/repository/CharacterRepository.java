package com.isekai.ssp.repository;

import com.isekai.ssp.entities.Character;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CharacterRepository extends JpaRepository<Character, Long> {
    List<Character> findByProjectId(Long projectId);
    Optional<Character> findByProjectIdAndName(Long projectId, String name);
}
