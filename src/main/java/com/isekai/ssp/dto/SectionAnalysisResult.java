package com.isekai.ssp.dto;

import java.util.List;

public record SectionAnalysisResult(
        List<DetectedSection> sections
) {
    public record DetectedSection(
            String type,
            String title,
            String summary,
            int sequenceNumber,
            List<String> keyConcepts
    ) {}
}
