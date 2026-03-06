package com.isekai.ssp.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO for LLM-generated quality assessment response.
 * Used with BeanOutputConverter for structured output.
 */
public record QualityAssessmentResult(
        float overallScore,
        Map<String, Float> dimensionScores,
        List<Issue> issues,
        String summary
) {
    public record Issue(
            String type,
            String severity,
            String location,
            String description,
            String suggestion
    ) {}
}
