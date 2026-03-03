package com.isekai.ssp.dto;

/**
 * Request body for creating or updating a Project.
 * sourceLanguage / targetLanguage use BCP-47 codes ("ja", "en", "zh", "ko", etc.)
 */
public record ProjectRequest(
        String title,
        String sourceLanguage,
        String targetLanguage,
        String description,
        /** Free-text prose style injected into the literary translation prompt.
         *  e.g. "dark fantasy, lyrical Dostoevsky-like inner monologue" */
        String translationStyle
) {}