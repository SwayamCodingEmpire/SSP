package com.isekai.ssp.dto;

import java.util.List;

public record TerminologyExtractionResult(
        List<DetectedTerm> terms
) {
    public record DetectedTerm(
            String term,
            String definition,
            String abbreviation,
            String category,
            List<String> crossReferences
    ) {}
}
