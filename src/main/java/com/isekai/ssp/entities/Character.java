package com.isekai.ssp.entities;
import com.isekai.ssp.helpers.CharacterRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "characters")
@Getter
@Setter
public class Character {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String name;                // Original name

    private String translatedName;      // Localized name

    @Column(columnDefinition = "TEXT")
    private String description;         // Physical/personality description

    @Column(columnDefinition = "TEXT")
    private String personalityTraits;   // JSON or comma-separated traits

    @Enumerated(EnumType.STRING)
    private CharacterRole role;         // PROTAGONIST, ANTAGONIST, SUPPORTING, MINOR

    private String qdrantVectorId;      // Reference to personality vector in QdrantDB

    private Integer firstAppearanceChapter;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "character1", cascade = CascadeType.ALL)
    private List<CharacterRelationship> relationshipsFrom;

    @OneToMany(mappedBy = "character2", cascade = CascadeType.ALL)
    private List<CharacterRelationship> relationshipsTo;
}