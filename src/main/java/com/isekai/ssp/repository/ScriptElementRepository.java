package com.isekai.ssp.repository;

import com.isekai.ssp.entities.ScriptElement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScriptElementRepository extends JpaRepository<ScriptElement, Long> {
    List<ScriptElement> findByChapterIdOrderBySequenceNumberAsc(Long chapterId);
    List<ScriptElement> findByProjectId(Long projectId);
}
