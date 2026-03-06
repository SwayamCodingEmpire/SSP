package com.isekai.ssp.entities;

import com.isekai.ssp.helpers.SectionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Generic section entity for non-narrative content (academic, non-fiction, periodical).
 * Replaces Scene for content types where "scenes" don't apply.
 */
@Entity
@Table(name = "content_sections", indexes = {
        @Index(name = "idx_content_section_chapter", columnList = "chapter_id")
})
@Getter
@Setter
public class ContentSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @Enumerated(EnumType.STRING)
    private SectionType type;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private Integer sequenceNumber;

    @Column(columnDefinition = "TEXT")
    private String keyConceptsJson;

    private String vectorDocId;

    private LocalDateTime createdAt;
}
