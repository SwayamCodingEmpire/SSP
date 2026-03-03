package com.isekai.ssp.dto;

import com.isekai.ssp.helpers.EmotionalTone;
import com.isekai.ssp.helpers.NarrativePace;
import com.isekai.ssp.helpers.NarrativeTimeType;
import com.isekai.ssp.helpers.SceneType;

import java.time.LocalDateTime;
import java.util.List;

public record SceneResponse(
        Long id,
        Long projectId,
        String summary,
        SceneType type,
        String location,
        Double tensionLevel,
        NarrativePace pace,
        EmotionalTone tone,
        NarrativeTimeType narrativeTimeType,
        Integer flashbackToChapter,
        List<Long> chapterIds,
        LocalDateTime createdAt
) {}