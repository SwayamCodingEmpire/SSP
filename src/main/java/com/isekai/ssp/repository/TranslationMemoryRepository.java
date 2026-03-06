package com.isekai.ssp.repository;

import com.isekai.ssp.entities.TranslationMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TranslationMemoryRepository extends JpaRepository<TranslationMemory, Long> {
    List<TranslationMemory> findByProjectId(Long projectId);
    List<TranslationMemory> findByProjectIdAndHumanVerifiedTrue(Long projectId);
    List<TranslationMemory> findBySourceChapterId(Long chapterId);
}
