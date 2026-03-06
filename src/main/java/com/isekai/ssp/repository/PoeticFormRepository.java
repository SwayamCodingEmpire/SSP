package com.isekai.ssp.repository;

import com.isekai.ssp.entities.PoeticForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PoeticFormRepository extends JpaRepository<PoeticForm, Long> {
    Optional<PoeticForm> findByChapterId(Long chapterId);
    List<PoeticForm> findByProjectId(Long projectId);
}
