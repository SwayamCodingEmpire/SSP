package com.isekai.ssp.repository;

import com.isekai.ssp.entities.ThematicElement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ThematicElementRepository extends JpaRepository<ThematicElement, Long> {
    List<ThematicElement> findByProjectId(Long projectId);
    List<ThematicElement> findByChapterId(Long chapterId);
}
