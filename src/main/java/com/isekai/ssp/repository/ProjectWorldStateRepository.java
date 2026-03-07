package com.isekai.ssp.repository;

import com.isekai.ssp.entities.ProjectWorldState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectWorldStateRepository extends JpaRepository<ProjectWorldState, Long> {

    /**
     * Returns the most recent world state snapshot for a project.
     * Returns empty Optional for projects with no analyzed chapters yet (chapter 1 scenario).
     *
     * Generates: SELECT ... WHERE project_id=? ORDER BY chapter_number DESC LIMIT 1
     */
    Optional<ProjectWorldState> findTopByProjectIdOrderByChapterNumberDesc(Long projectId);
}
