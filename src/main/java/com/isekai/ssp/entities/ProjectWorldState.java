package com.isekai.ssp.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Stores an AI-generated compact narrative world state after each analyzed chapter.
 * Injected as a fixed-size context block into the NEXT chapter's extraction prompt
 * to provide deduplication safety and faction/tension continuity without dumping
 * all project characters into every prompt.
 *
 * One row per analyzed chapter per project. The latest row is retrieved via
 * findTopByProjectIdOrderByChapterNumberDesc — no vector embedding needed.
 */
@Entity
@Table(name = "project_world_states",
        indexes = {
                @Index(name = "idx_world_state_project_chapter", columnList = "project_id,chapter_number")
        })
@Getter
@Setter
public class ProjectWorldState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** The chapter number AFTER which this world state was generated. */
    @Column(nullable = false)
    private Integer chapterNumber;

    /**
     * AI-generated compact narrative summary (~350 words).
     * Contains: active factions/tensions, active cast, dormant characters,
     * unresolved threads, relationship shifts.
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String summary;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
