package com.isekai.ssp.repository;

import com.isekai.ssp.entities.StyleExample;
import com.isekai.ssp.helpers.SceneType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StyleExampleRepository extends JpaRepository<StyleExample, Long> {

    List<StyleExample> findByProjectId(Long projectId);

    List<StyleExample> findByProjectIdAndSceneType(Long projectId, SceneType sceneType);
}