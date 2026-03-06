package com.isekai.ssp.dto;

import java.time.LocalDateTime;

public record PersonalityResponse(
        Long id,
        String name,
        String description,
        String personalityTraits,
        String voiceExample,
        String triggerCondition,
        boolean isPrimary,
        LocalDateTime createdAt
) {}
