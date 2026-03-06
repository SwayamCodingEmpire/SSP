package com.isekai.ssp.dto;

import com.isekai.ssp.helpers.CharacterRole;

import java.time.LocalDateTime;
import java.util.List;

public record CharacterResponse(
        Long id,
        Long projectId,
        String name,
        String translatedName,
        List<String> aliases,
        String description,
        String personalityTraits,
        CharacterRole role,
        String voiceExample,
        Integer firstAppearanceChapter,
        List<PersonalityResponse> personalities,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}