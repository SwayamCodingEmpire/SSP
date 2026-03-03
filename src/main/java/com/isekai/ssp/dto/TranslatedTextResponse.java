package com.isekai.ssp.dto;

import com.isekai.ssp.helpers.TranslationStatus;

import java.time.LocalDateTime;

public record TranslatedTextResponse(
        Long chapterId,
        Integer chapterNumber,
        String title,
        TranslationStatus translationStatus,
        /** AI-generated translation. Always present once translation completes. */
        String translatedText,
        /** User's edited version. Non-null only when userAccepted=false. */
        String userEditedText,
        /** null = not yet reviewed, true = accepted AI output, false = user modified it */
        Boolean userAccepted,
        LocalDateTime reviewedAt,
        boolean chunked,
        Integer totalSegments,
        Integer translatedSegments
) {}