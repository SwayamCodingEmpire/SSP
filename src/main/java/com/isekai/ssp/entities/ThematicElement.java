package com.isekai.ssp.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Theme, symbol, motif, or allusion tracked across chapters.
 * Used for poetry, essays, and non-fiction content.
 */
@Entity
@Table(name = "thematic_elements", indexes = {
        @Index(name = "idx_thematic_element_project", columnList = "project_id")
})
@Getter
@Setter
public class ThematicElement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    @Column(nullable = false)
    private String themeType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String occurrencesJson;

    private String vectorDocId;

    private LocalDateTime createdAt;
}
