package com.isekai.ssp.dto;

import com.isekai.ssp.helpers.RelationshipType;

import java.time.LocalDateTime;

public record CharacterRelationshipResponse(
        Long id,
        Long character1Id,
        String character1Name,
        String character1TranslatedName,
        Long character2Id,
        String character2Name,
        String character2TranslatedName,
        RelationshipType type,
        String description,
        Double affinity,
        Integer establishedAtChapter,
        LocalDateTime createdAt
) {}