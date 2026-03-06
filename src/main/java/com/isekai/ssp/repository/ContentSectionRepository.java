package com.isekai.ssp.repository;

import com.isekai.ssp.entities.ContentSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentSectionRepository extends JpaRepository<ContentSection, Long> {
    List<ContentSection> findByChapterIdOrderBySequenceNumberAsc(Long chapterId);
    List<ContentSection> findByProjectId(Long projectId);
}
