package com.isekai.ssp.dto;

import java.util.List;

public record CharacterExtractionResult(
        List<ExtractedCharacter> characters
) {
    public record ExtractedCharacter(
            String name,
            String description,
            String personalityTraits,
            String role
    ) {}
}
