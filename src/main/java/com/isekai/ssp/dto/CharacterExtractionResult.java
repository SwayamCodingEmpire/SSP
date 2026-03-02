package com.isekai.ssp.dto;

import java.util.List;

public record CharacterExtractionResult(
        List<ExtractedCharacter> characters,
        List<ExtractedRelationship> relationships
) {
    public record ExtractedCharacter(
            String name,
            String description,
            String personalityTraits,
            String role,
            /** One representative dialogue line showing how this character speaks */
            String voiceExample,
            /** Emotional/mental state in this chapter: "grief-stricken, resolute" */
            String emotionalState,
            /** Primary motivation driving this character right now */
            String currentGoal,
            /** Narrative arc stage: "reluctant hero", "crossing threshold", "betrayed" */
            String arcStage
    ) {}

    public record ExtractedRelationship(
            String character1Name,
            String character2Name,
            /** One of: ALLY, ENEMY, FAMILY, ROMANTIC, NEUTRAL, MENTOR */
            String type,
            /** Descriptive label: "Childhood friends", "Sworn enemies", "Mentor-student" */
            String description,
            /** -1.0 (hostile) to 1.0 (deeply bonded). Use 0.0 for NEUTRAL. */
            double affinity
    ) {}
}