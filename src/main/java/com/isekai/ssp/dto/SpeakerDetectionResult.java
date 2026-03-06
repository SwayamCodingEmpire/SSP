package com.isekai.ssp.dto;

import java.util.List;

public record SpeakerDetectionResult(
        List<CharacterDialogue> characterDialogues
) {
    public record CharacterDialogue(
            String characterName,
            String emotionType,
            double emotionIntensity,
            String dialogueSummary,
            /**
             * Which personality/alter ego was speaking in this chapter.
             * Matches CharacterPersonality.name. Null if single-personality or base personality.
             * e.g. "Hyde" when Hyde is speaking, null when Jekyll is.
             */
            String activePersonalityName
    ) {}
}
