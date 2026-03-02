package com.isekai.ssp.entities;

import com.isekai.ssp.helpers.TranslationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A text chunk within a chapter. Only created when a chapter is too large
 * to process in a single AI call and needs to be split.
 */
@Entity
@Table(name = "segments")
@Getter
@Setter
public class Segment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @Column(nullable = false)
    private Integer sequenceNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalText;

    @Column(columnDefinition = "TEXT")
    private String translatedText;

    @Enumerated(EnumType.STRING)
    private TranslationStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime translatedAt;
}
