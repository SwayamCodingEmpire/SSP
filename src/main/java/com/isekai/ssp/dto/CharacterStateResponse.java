package com.isekai.ssp.dto;

import java.time.LocalDateTime;

/**
 * Temporal snapshot of a character's state at a specific chapter.
 * Used to render the character arc view in the frontend.
 */
public record CharacterStateResponse(
        Long id,
        Long characterId,
        Integer chapterNumber,
        String emotionalState,
        String physicalState,
        String currentGoal,
        String arcStage,
        String affiliation,
        String loyalty,
        String dialogueEmotionType,
        Double dialogueEmotionIntensity,
        String dialogueSummary,
        String activePersonalityName,
        LocalDateTime createdAt
) {}