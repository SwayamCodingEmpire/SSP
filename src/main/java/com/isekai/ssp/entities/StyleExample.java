package com.isekai.ssp.entities;

import com.isekai.ssp.helpers.SceneType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A project-specific few-shot translation example for a given scene type.
 * Retrieved via RAG during Pass 2 to show the AI how literary elevation
 * should look for THIS project's style in THAT scene type.
 *
 * Generic few-shot examples hurt performance; project-specific, scene-matched
 * examples improve stylistic consistency by 2-4x (per translation research).
 */
@Entity
@Table(name = "style_examples",
        indexes = @Index(name = "idx_style_example_project_scene", columnList = "project_id, scene_type"))
@Getter
@Setter
public class StyleExample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** The scene type this example applies to — used for filtered RAG retrieval */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SceneType sceneType;

    /** Original source text (in source language) */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String sourceText;

    /** Approved literary translation in the target language */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String targetText;

    /** Brief explanation of notable creative decisions in this translation */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Reference to the pgvector document for semantic similarity search */
    private String vectorDocId;

    private LocalDateTime createdAt;
}