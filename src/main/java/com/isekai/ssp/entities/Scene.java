package com.isekai.ssp.entities;

import com.isekai.ssp.helpers.EmotionalTone;
import com.isekai.ssp.helpers.NarrativePace;
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

    private String qdrantVectorId;

    private LocalDateTime createdAt;
}
