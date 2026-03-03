package com.isekai.ssp.dto;

import com.isekai.ssp.helpers.ChapterStatus;

public record ChapterProcessingResponse(
        Long chapterId,
        Integer chapterNumber,
        String title,
        ChapterStatus status,
        String translationStatus,
        String analysisStatus,
        String originalTextPreview,
        String fullOriginalText
) {}
