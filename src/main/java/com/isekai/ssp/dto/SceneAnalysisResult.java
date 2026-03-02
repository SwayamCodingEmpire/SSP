package com.isekai.ssp.dto;

import java.util.List;

public record SceneAnalysisResult(
        List<DetectedScene> scenes
) {
    public record DetectedScene(
            String summary,
            String type,
            String location,
            double tensionLevel,
            String pace,
            String tone,
            boolean continuedFromPrevious,
            boolean continuesInNext
    ) {}
}
