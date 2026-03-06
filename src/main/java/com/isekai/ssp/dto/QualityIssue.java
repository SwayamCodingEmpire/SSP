package com.isekai.ssp.dto;

public record QualityIssue(
        String type,
        String severity,
        String location,
        String description,
        String suggestion
) {}
