package com.isekai.ssp.entities;

import com.isekai.ssp.helpers.ScriptElementType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Script element for dramatic content (TV/movie scripts).
 * Captures scene headings, character cues, dialogue blocks, stage directions.
 */
@Entity
@Table(name = "script_elements", indexes = {
        @Index(name = "idx_script_element_chapter", columnList = "chapter_id")
})
@Getter
@Setter
public class ScriptElement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @Enumerated(EnumType.STRING)
    private ScriptElementType type;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String characterName;

    private Integer sequenceNumber;

    @Column(columnDefinition = "TEXT")
    private String parenthetical;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private String vectorDocId;

    private LocalDateTime createdAt;
}
