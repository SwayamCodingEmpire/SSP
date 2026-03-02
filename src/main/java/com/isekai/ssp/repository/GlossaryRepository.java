package com.isekai.ssp.repository;

import com.isekai.ssp.entities.Glossary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GlossaryRepository extends JpaRepository<Glossary, Long> {
    List<Glossary> findByProjectId(Long projectId);
    List<Glossary> findByProjectIdAndEnforceConsistencyTrue(Long projectId);
    Optional<Glossary> findByProjectIdAndOriginalTerm(Long projectId, String originalTerm);
}
