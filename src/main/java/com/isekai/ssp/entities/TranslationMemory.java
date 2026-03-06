package com.isekai.ssp.entities;

import com.isekai.ssp.helpers.ContentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Segment-level translation memory storage.
 * Approved human-reviewed translations are stored here for fuzzy matching
 * during future translation. Embedded in pgvector for semantic retrieval.
 */
@Entity
@Table(name = "translation_memory", indexes = {
        @Index(name = "idx_tm_project", columnList = "project_id"),
        @Index(name = "idx_tm_language_pair", columnList = "source_language, target_language")
})
@Getter
@Setter
public class TranslationMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "source_language", nullable = false)
    private String sourceLanguage;

    @Column(name = "target_language", nullable = false)
    private String targetLanguage;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String sourceSegment;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String targetSegment;

    @Enumerated(EnumType.STRING)
    private ContentType contentType;

    private String domainTag;

    private Float qualityScore;

    private Boolean humanVerified;

    private String vectorDocId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_chapter_id")
    private Chapter sourceChapter;

    @Column(nullable = false)
    private LocalDateTime createdAt;


    private LocalDateTime updatedAt;
}
