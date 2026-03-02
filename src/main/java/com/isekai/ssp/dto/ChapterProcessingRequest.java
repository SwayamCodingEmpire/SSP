package com.isekai.ssp.dto;

/**
 * Request DTO for processing a chapter.
 * Supports any Unicode language in chapterText.
 */
public record ChapterProcessingRequest(
        Long projectId,
        Integer chapterNumber,
        String title,           // Can be in source language
        String chapterText      // Full chapter text in source language (Unicode)
) {
}
