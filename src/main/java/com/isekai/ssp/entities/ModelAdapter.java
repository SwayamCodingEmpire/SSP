package com.isekai.ssp.entities;

import com.isekai.ssp.helpers.ContentFamily;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Tracks fine-tuned LoRA adapters available for vLLM.
 * When vLLM is running, the routing strategy can select the appropriate adapter
 * based on content family and language pair.
 */
@Entity
@Table(name = "model_adapters")
@Getter
@Setter
public class ModelAdapter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String baseModel;

    @Enumerated(EnumType.STRING)
    private ContentFamily targetFamily;

    private String adapterPath;

    private String languagePair;

    private Float qualityScore;

    private Boolean active;

    private LocalDateTime createdAt;
}
