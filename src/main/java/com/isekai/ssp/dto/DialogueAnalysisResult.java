package com.isekai.ssp.dto;

import java.util.List;

public record DialogueAnalysisResult(
        List<DetectedElement> elements
) {
    public record DetectedElement(
            String type,
            String content,
            String characterName,
            int sequenceNumber,
            String parenthetical,
            String notes
    ) {}
}
