package com.isekai.ssp.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Temporal snapshot of a character's state at a specific chapter.
 * Enables flashback-aware context retrieval: when translating a flashback to chapter 5,
 * retrieve the character's state AT chapter 5, not their current state.
 */
@Entity
@Table(name = "character_states",
        indexes = {
                @Index(name = "idx_char_state_character", columnList = "character_id"),
                @Index(name = "idx_char_state_chapter", columnList = "chapter_id")
        })
@Getter
@Setter
public class CharacterState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    /** Denormalized for efficient temporal range queries without joins */
    @Column(nullable = false)
    private Integer chapterNumber;

    /** Emotional/mental state at this chapter: "grief-stricken, resolute, hiding guilt" */
    @Column(columnDefinition = "TEXT")
    private String emotionalState;

    /** Physical condition at this chapter: "recovering from battle wound, exhausted" */
    @Column(columnDefinition = "TEXT")
    private String physicalState;

    /** Primary motivation driving the character at this point in the story */
    @Column(columnDefinition = "TEXT")
    private String currentGoal;

    /** Narrative arc position: "reluctant hero", "crossing threshold", "darkest moment" */
    @Column(columnDefinition = "TEXT")
    private String arcStage;

    /** Additional free-form notes about the character at this chapter */
    @Column(columnDefinition = "TEXT")
    private String narrativeNotes;

    /**
     * Flexible JSONB bag for additional attributes: knowledge state, relationships at this point,
     * key events that just occurred, items carried, etc.
     * Stored as JSONB for efficient partial querying.
     */
    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String attributes;

    /** Reference to the pgvector document for semantic similarity search */
    private String vectorDocId;

    private LocalDateTime createdAt;
}