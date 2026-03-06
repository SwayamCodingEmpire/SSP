package com.isekai.ssp.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A distinct personality, alter ego, or dissociative identity of a character.
 *
 * Characters with multiple personalities (Jekyll/Hyde, Yugi/Yami Yugi, Gollum/Sméagol)
 * require separate voice profiles — each personality speaks with a different register,
 * vocabulary, and emotional texture. Treating them as the same voice produces bad translations.
 *
 * One CharacterPersonality is marked isPrimary=true (the base/default personality).
 * Additional rows represent alter egos, possessed states, or dissociative identities.
 *
 * Each personality gets its own pgvector embedding so the translator can retrieve
 * the correct voice profile by semantic similarity to the active personality's dialogue.
 */
@Entity
@Table(name = "character_personalities",
        indexes = @Index(name = "idx_personality_character", columnList = "character_id"))
@Getter
@Setter
public class CharacterPersonality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    /**
     * The name of this personality/alter ego.
     * e.g. "Hyde", "Yami Yugi", "The Hollow", "Gollum"
     * Use the character's primary name for the base personality (isPrimary=true).
     */
    @Column(nullable = false)
    private String name;

    /**
     * How this personality presents — physical changes, behavioral markers,
     * speech mannerisms, distinctive features that signal this personality is active.
     * e.g. "Eyes turn red, voice drops an octave, formal speech drops entirely"
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Comma-separated personality traits specific to this alter ego.
     * e.g. "violent, cunning, sadistic, fearless" (Hyde)
     * vs "compassionate, strategic, self-sacrificing" (Jekyll)
     */
    @Column(columnDefinition = "TEXT")
    private String personalityTraits;

    /**
     * A single representative line of dialogue that captures this personality's unique voice.
     * This is the key field for translation — it anchors the AI to the right register.
     * e.g. "You think you can break me? I've broken gods." (Hyde)
     * vs  "Please, you must understand — I never meant for this." (Jekyll)
     */
    @Column(columnDefinition = "TEXT")
    private String voiceExample;

    /**
     * What triggers or activates this personality.
     * e.g. "Emerges under mortal threat or extreme rage"
     *      "Activated when someone issues a game challenge"
     *      "Surfaces when Frodo puts on the Ring"
     * Null for the primary personality (always present).
     */
    @Column(columnDefinition = "TEXT")
    private String triggerCondition;

    /**
     * True for the character's default/base personality.
     * Exactly one personality per character should have isPrimary=true.
     */
    @Column(nullable = false)
    private boolean isPrimary = false;

    /** pgvector document ID for semantic retrieval of this personality's voice profile */
    private String vectorDocId;

    private LocalDateTime createdAt;
}
