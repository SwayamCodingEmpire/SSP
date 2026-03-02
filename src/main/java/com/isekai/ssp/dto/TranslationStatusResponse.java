package com.isekai.ssp.dto;

public record TranslationStatusResponse(
        Long chapterId,
        String status,
        String provider
) {}
