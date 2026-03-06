package com.isekai.ssp.dto;

import java.util.List;

public record ThemeExtractionResult(
        List<DetectedTheme> themes
) {
    public record DetectedTheme(
            String themeType,
            String name,
            String description,
            List<String> occurrences
    ) {}
}
