package com.isekai.ssp.entities;

import com.isekai.ssp.helpers.AnalysisStatus;
import com.isekai.ssp.helpers.ChapterStatus;
import com.isekai.ssp.helpers.TranslationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "chapters")
@Getter
@Setter
public class Chapter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private Integer chapterNumber;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String originalText;          // Full chapter text in source language

    @Column(columnDefinition = "TEXT")
    private String translatedText;        // AI-generated translated text (or rejoined from segments)

    @Column(columnDefinition = "TEXT")
    private String userEditedText;        // User's final version after review/editing

    private Boolean userAccepted;         // true = accepted AI output as-is, false = user made edits
    private LocalDateTime reviewedAt;     // when the user saved their review

    @Column(columnDefinition = "TEXT")
    private String summary;               // AI-generated summary

    @Enumerated(EnumType.STRING)
    private ChapterStatus status;

    @Enumerated(EnumType.STRING)
    private TranslationStatus translationStatus;

    @Enumerated(EnumType.STRING)
    private AnalysisStatus analysisStatus;

    @Column(columnDefinition = "TEXT")
    private String contextNotes;          // AI translation context notes

    private boolean chunked;              // true if chapter was split into segments

    private Integer totalSegments;
    private Integer translatedSegments;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "chapter", cascade = CascadeType.ALL)
    private List<Segment> segments;

    @ManyToMany(mappedBy = "chapters")
    private List<Scene> scenes;
}
