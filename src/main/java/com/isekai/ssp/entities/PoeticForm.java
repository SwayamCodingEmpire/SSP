package com.isekai.ssp.entities;

import com.isekai.ssp.helpers.VerseForm;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Verse form analysis result for poetry content.
 * Captures meter, rhyme scheme, stanza structure per chapter.
 */
@Entity
@Table(name = "poetic_forms", indexes = {
        @Index(name = "idx_poetic_form_chapter", columnList = "chapter_id")
})
@Getter
@Setter
public class PoeticForm {

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
    private VerseForm form;

    @Column(columnDefinition = "TEXT")
    private String meterPattern;

    @Column(columnDefinition = "TEXT")
    private String rhymeScheme;

    private Integer stanzaCount;
    private Integer linesPerStanza;

    @Column(columnDefinition = "TEXT")
    private String soundDevicesJson;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private String vectorDocId;

    private LocalDateTime createdAt;
}
