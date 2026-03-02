package com.isekai.ssp.entities;

import com.isekai.ssp.helpers.EmotionalTone;
import com.isekai.ssp.helpers.NarrativePace;
import com.isekai.ssp.helpers.NarrativeTimeType;
import com.isekai.ssp.helpers.SceneType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "scenes")
@Getter
@Setter
public class Scene {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToMany
    @JoinTable(
            name = "chapter_scenes",
            joinColumns = @JoinColumn(name = "scene_id"),
            inverseJoinColumns = @JoinColumn(name = "chapter_id")
    )
    private List<Chapter> chapters;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    private SceneType type;

    @Column(columnDefinition = "TEXT")
    private String location;

    private Double tensionLevel;

    @Enumerated(EnumType.STRING)
    private NarrativePace pace;

    @Enumerated(EnumType.STRING)
    private EmotionalTone tone;

    /** Reference to the pgvector document for semantic similarity search */
    private String vectorDocId;

    /**
     * Temporal classification of this scene.
     * FLASHBACK scenes trigger retrieval of historical character states.
     */
    @Enumerated(EnumType.STRING)
    private NarrativeTimeType narrativeTimeType;

    /**
     * For FLASHBACK scenes: the chapter number this scene is set in.
     * Enables retrieval of character states at that earlier point in the story.
     */
    private Integer flashbackToChapter;

    private LocalDateTime createdAt;
}
