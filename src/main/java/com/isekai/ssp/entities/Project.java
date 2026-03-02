package com.isekai.ssp.entities;

import com.isekai.ssp.helpers.FileFormat;
import com.isekai.ssp.helpers.ProjectStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "projects")
@Getter
@Setter
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String sourceLanguage;      // e.g., "ja", "en", "zh"

    @Column(nullable = false)
    private String targetLanguage;      // e.g., "en", "es", "fr"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;       // DRAFT, PARSING, IN_PROGRESS, REVIEW, COMPLETED

    @Column(columnDefinition = "TEXT")
    private String description;

    private String originalFileName;

    @Enumerated(EnumType.STRING)
    private FileFormat fileFormat;      // EPUB, TXT, PDF, DOCX

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // Free-text prose style descriptor injected into the translation prompt.
    // e.g. "dark fantasy, lyrical and introspective, Dostoevsky-like inner monologue"
    // or "gritty noir, sparse Hemingway-esque sentences with brutal economy of words"
    @Column(columnDefinition = "TEXT")
    private String translationStyle;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    private List<Chapter> chapters;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    private List<Character> characters;
}