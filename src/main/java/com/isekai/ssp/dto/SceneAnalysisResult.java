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
            boolean continuesInNext,
            /**
             * Temporal classification: PRESENT, FLASHBACK, or FLASH_FORWARD.
             * Most scenes are PRESENT. Use FLASHBACK when the narrative explicitly
             * moves to events that happened before the story's current timeline.
             */
            String narrativeTimeType,
            /**
             * For FLASHBACK scenes only: the approximate chapter number this scene is set in.
             * Leave null for PRESENT and FLASH_FORWARD scenes.
             */
            Integer flashbackToChapter
    ) {}
}