package com.isekai.ssp.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Persisted quality estimation result per chapter translation.
 */
@Entity
@Table(name = "quality_scores", indexes = {
        @Index(name = "idx_quality_score_chapter", columnList = "chapter_id", unique = true)
})
@Getter
@Setter
public class QualityScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    private Float overallScore;

    @Column(columnDefinition = "TEXT")
    private String dimensionScoresJson;

    @Column(columnDefinition = "TEXT")
    private String issuesJson;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private Boolean flaggedForReview;

    private LocalDateTime createdAt;
}
