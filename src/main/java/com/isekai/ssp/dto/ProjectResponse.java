package com.isekai.ssp.dto;

import com.isekai.ssp.helpers.FileFormat;
import com.isekai.ssp.helpers.ProjectStatus;

import java.time.LocalDateTime;

public record ProjectResponse(
        Long id,
        String title,
        String sourceLanguage,
        String targetLanguage,
        ProjectStatus status,
        String description,
        String originalFileName,
        FileFormat fileFormat,
        String translationStyle,
        int chapterCount,
        int characterCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}