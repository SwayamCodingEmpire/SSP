package com.isekai.ssp.repository;

import com.isekai.ssp.entities.Scene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SceneRepository extends JpaRepository<Scene, Long> {

    @Query("SELECT s FROM Scene s JOIN s.chapters c WHERE c.id = :chapterId")
    List<Scene> findByChapterId(Long chapterId);

    List<Scene> findByProjectId(Long projectId);
}
