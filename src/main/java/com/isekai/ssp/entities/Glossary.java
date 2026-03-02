package com.isekai.ssp.entities;
import com.isekai.ssp.helpers.GlossaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "glossary", indexes = {
        @Index(name = "idx_glossary_project_term", columnList = "project_id, originalTerm", unique = true)
})
@Getter
@Setter
public class Glossary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String originalTerm;        // Original language term

    @Column(nullable = false)
    private String translatedTerm;      // Target language translation

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GlossaryType type;          // CHARACTER_NAME, LOCATION, MAGIC_SPELL, ITEM, CONCEPT, ORGANIZATION

    @Column(columnDefinition = "TEXT")
    private String contextNotes;        // When/how to use this term

    @Column(columnDefinition = "TEXT")
    private String alternativeTranslations; // JSON array of alternatives

    private Boolean enforceConsistency; // If true, always use this translation

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
