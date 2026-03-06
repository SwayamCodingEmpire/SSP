package com.isekai.ssp.entities;
import com.isekai.ssp.helpers.CharacterRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private String name;                // Canonical (true) name — updated on identity reveal

    private String translatedName;      // Localized name

    /**
     * All known aliases, false names, titles, and epithets this character has been referred to.
     * e.g. ["The Masked Man", "The Magus Killer", "Father"]
     * Stored as JSONB array. Updated whenever a new alias or identity reveal is detected.
     */
    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> aliases = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String description;         // Physical/personality description

    @Column(columnDefinition = "TEXT")
    private String personalityTraits;   // JSON or comma-separated traits

    @Enumerated(EnumType.STRING)
    private CharacterRole role;         // PROTAGONIST, ANTAGONIST, SUPPORTING, MINOR

    /** Reference to the pgvector document for semantic similarity search */
    private String vectorDocId;

    /**
     * A single representative line of dialogue that captures this character's voice.
     * Injected as a few-shot example in translation prompts to anchor their register.
     * e.g. "You think you can stop me? I've buried better men than you."
     */
    @Column(columnDefinition = "TEXT")
    private String voiceExample;

    private Integer firstAppearanceChapter;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "character1", cascade = CascadeType.ALL)
    private List<CharacterRelationship> relationshipsFrom;

    @OneToMany(mappedBy = "character2", cascade = CascadeType.ALL)
    private List<CharacterRelationship> relationshipsTo;

    /**
     * All personalities/alter egos for this character.
     * Single-personality characters have exactly one entry (isPrimary=true).
     * Multi-personality characters have one primary + N alter ego entries.
     */
    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CharacterPersonality> personalities;
}