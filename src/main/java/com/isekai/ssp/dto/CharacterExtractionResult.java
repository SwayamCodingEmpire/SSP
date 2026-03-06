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
            String loyalty,
            /**
             * All distinct personalities/alter egos observed for this character in this chapter.
             * Single-personality characters: one entry with isPrimary=true.
             * Multi-personality characters: list each observed personality as a separate entry.
             */
            List<ExtractedPersonality> personalities,
            /**
             * The name of the personality that was dominant/active in this chapter.
             * Must match one of the names in the personalities list.
             * Null if only the primary personality appeared.
             */
            String activePersonalityName
    ) {}

    public record ExtractedPersonality(
            /** Name of this personality/alter ego: "Hyde", "Yami Yugi", "Gollum" */
            String name,
            /** How this personality presents — behavioral markers, physical signs */
            String description,
            /** Comma-separated traits: "violent, cunning, fearless" */
            String personalityTraits,
            /** One representative dialogue line capturing this personality's unique voice */
            String voiceExample,
            /** What triggers/activates this personality. Null for the primary personality. */
            String triggerCondition,
            /** True for the character's default/base personality */
            boolean isPrimary
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