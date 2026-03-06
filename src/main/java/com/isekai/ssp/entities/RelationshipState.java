package com.isekai.ssp.entities;

import com.isekai.ssp.helpers.RelationshipType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Temporal snapshot of a character relationship at a specific chapter.
 *
 * Relationships evolve over a story's arc: allies betray, enemies reconcile,
 * strangers become family. A flat CharacterRelationship record cannot capture this.
 * Each chapter that features the pair gets a new RelationshipState, preserving
 * the full arc for flashback-aware context retrieval.
 *
 * Example arc for Shirou <-> Archer:
 *   Ch 1:  NEUTRAL,  affinity=0.0, "Strangers thrown together"
 *   Ch 8:  ALLY,     affinity=0.5, "Uneasy partners"
 *   Ch 15: ENEMY,    affinity=-0.7, "True identity revealed — Shirou's future self"
 *   Ch 24: ALLY,     affinity=0.9, "Accepted each other, fighting as one"
 */
@Entity
@Table(name = "relationship_states",
        indexes = {
                @Index(name = "idx_rel_state_relationship", columnList = "relationship_id"),
                @Index(name = "idx_rel_state_chapter", columnList = "chapter_id")
        })
@Getter
@Setter
public class RelationshipState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "relationship_id", nullable = false)
    private CharacterRelationship relationship;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    /** Denormalized for efficient temporal range queries without joins */
    @Column(nullable = false)
    private Integer chapterNumber;

    /** Relationship type at this chapter — may have shifted from its original type */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RelationshipType type;

    /** How this relationship is described at this point in the story */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Affinity at this chapter: -1.0 (deeply hostile) to 1.0 (deeply bonded) */
    private Double affinity;

    /**
     * Notes how this relationship manifests differently when a character's alter ego is active.
     * e.g. "Other characters fear Hyde but trust Jekyll — affinity swings from -0.8 to 0.6
     *       depending on which personality they are currently facing."
     * Null when no personality switching is relevant to this relationship in this chapter.
     */
    @Column(columnDefinition = "TEXT")
    private String activePersonalityContext;

    /**
     * What specifically happened to this relationship in this chapter.
     * Captures relationship-changing events, revelations, and emotional shifts.
     * e.g. "Shirou discovered Archer's true identity, shattering their uneasy truce"
     *      "Reconciled after years of enmity when protecting the same person"
     * Null if the relationship was present but unchanged.
     */
    @Column(columnDefinition = "TEXT")
    private String dynamicsNote;

    /** Reference to the pgvector document for semantic similarity search */
    private String vectorDocId;

    private LocalDateTime createdAt;
}