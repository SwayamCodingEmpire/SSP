package com.isekai.ssp.dto;

import java.util.List;

public record AnalysisStatusResponse(
        Long chapterId,
        String status,
        int charactersFound,
        int scenesDetected,
        List<String> characterNames,
        List<ScenePreview> scenes
) {
    public record ScenePreview(
            String type,
            String summary,
            double tensionLevel
    ) {}
}
