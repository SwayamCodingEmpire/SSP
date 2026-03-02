package com.isekai.ssp.dto;

import java.util.List;

public record SpeakerDetectionResult(
        List<CharacterDialogue> characterDialogues
) {
    public record CharacterDialogue(
            String characterName,
            String emotionType,
            double emotionIntensity,
            String dialogueSummary
    ) {}
}
