package com.isekai.ssp.entities;
import com.isekai.ssp.helpers.RelationshipType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "character_relationships")
@Getter
@Setter
public class CharacterRelationship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character1_id", nullable = false)
    private Character character1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character2_id", nullable = false)
    private Character character2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RelationshipType type;      // ALLY, ENEMY, FAMILY, ROMANTIC, NEUTRAL, MENTOR

    @Column(columnDefinition = "TEXT")
    private String description;         // "Childhood friends", "Sworn enemies"

    private Double affinity;            // -1.0 (hostile) to 1.0 (close)

    private Integer establishedAtChapter;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
