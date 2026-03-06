package com.isekai.ssp.dto;

import java.util.List;
import java.util.Map;

public record QualityReport(
        float overallScore,
        Map<String, Float> dimensionScores,
        List<QualityIssue> issues,
        String summary
) {}
