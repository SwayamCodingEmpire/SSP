package com.isekai.ssp.repository;

import com.isekai.ssp.entities.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    List<Chapter> findByProjectIdOrderByChapterNumber(Long projectId);
}
