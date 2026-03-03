package com.isekai.ssp.dto;

import java.util.List;

public record CharacterExtractionResult(
        List<ExtractedCharacter> characters,
        List<ExtractedRelationship> relationships
) {
    public record ExtractedCharacter(
            String name,
            /** Romanized or translated name in the target language (e.g. "Emiya Shirou" for "衛宮士郎") */
            String translatedName,
            String description,
            String personalityTraits,
            String role,
            /** One representative dialogue line showing how this character speaks */
            String voiceExample,
            /** Emotional/mental state in this chapter: "grief-stricken, resolute" */
            String emotionalState,
            /** Primary motivation driving this character right now */
            String currentGoal,
            /** Narrative arc position: "reluctant hero", "crossing threshold", "betrayed" */
            String arcStage,
            /**
             * Alternative names, titles, or epithets this character is called in this chapter.
             * e.g. ["The Masked Man", "The Magus Killer"]. Use empty array [] if none.
             */
            List<String> aliases,
            /**
             * Faction, guild, or organization the character belongs to at this chapter.
             * Null when not applicable. e.g. "Fairy Tail Guild", "Magic Council".
             */
            String affiliation,
            /**
             * Who or what the character is loyal to — may differ from affiliation.
             * e.g. "Fiercely loyal to the King", "Secretly working for the enemy faction".
             * Null when not determined.
             */
            String loyalty
    ) {}

    public record ExtractedRelationship(
            String character1Name,
            String character2Name,
            /** One of: ALLY, ENEMY, FAMILY, ROMANTIC, NEUTRAL, MENTOR */
            String type,
            /** Descriptive label: "Childhood friends", "Sworn enemies", "Mentor-student" */
            String description,
            /** -1.0 (hostile) to 1.0 (deeply bonded). Use 0.0 for NEUTRAL. */
            double affinity,
            /**
             * What specifically happened to this relationship in THIS chapter.
             * Captures shifts, revelations, and turning points.
             * e.g. "Shirou discovered Archer's true identity, shattering their uneasy truce."
             * Null if the relationship appears but nothing significant changed.
             */
            String dynamicsNote
    ) {}
}