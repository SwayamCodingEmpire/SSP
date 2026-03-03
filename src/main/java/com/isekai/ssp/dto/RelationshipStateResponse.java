package com.isekai.ssp.dto;

import com.isekai.ssp.helpers.RelationshipType;

import java.time.LocalDateTime;

/**
 * Temporal snapshot of a character relationship at a specific chapter.
 * Used to render the relationship arc view in the frontend.
 */
public record RelationshipStateResponse(
        Long id,
        Long relationshipId,
        Integer chapterNumber,
        RelationshipType type,
        String description,
        Double affinity,
        String dynamicsNote,
        LocalDateTime createdAt
) {}