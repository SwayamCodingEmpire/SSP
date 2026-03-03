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
     * Dominant emotion detected from dialogue in this chapter.
     * Values: NEUTRAL, HAPPY, SAD, ANGRY, FEARFUL, URGENT, EXCITED, CONTEMPLATIVE
     * Populated by SpeakerDetectionService after CharacterExtraction.
     */
    private String dialogueEmotionType;

    /**
     * Intensity of the dominant dialogue emotion (0.0 = barely perceptible, 1.0 = overwhelming).
     * Populated by SpeakerDetectionService.
     */
    private Double dialogueEmotionIntensity;

    /**
     * How this character speaks in this specific chapter — register, patterns, verbal habits.
     * e.g. "speaks in short clipped sentences, deflects personal questions, formal with strangers"
     * Key for flashback voice fidelity: lets the translator use the character's voice AT this chapter,
     * not their current voice.
     * Populated by SpeakerDetectionService.
     */
    @Column(columnDefinition = "TEXT")
    private String dialogueSummary;

    /**
     * Faction, guild, organization, or group the character belongs to at this chapter.
     * Null when not applicable (solo characters, undefined allegiance).
     * e.g. "Fairy Tail Guild", "Magic Council", "Shadow Monarch's Army"
     * Can change across chapters (defection, capture, promotion).
     */
    @Column(columnDefinition = "TEXT")
    private String affiliation;

    /**
     * Who or what the character is loyal to at this chapter.
     * Tracks hidden loyalties and shifts — often differs from affiliation.
     * e.g. "Fiercely loyal to the King", "Secretly working for the enemy faction",
     *      "Loyal only to themselves", "Torn between family and duty"
     */
    @Column(columnDefinition = "TEXT")
    private String loyalty;

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