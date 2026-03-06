package com.isekai.ssp.domain;

import com.isekai.ssp.helpers.ContentType;

/**
 * Holds all context needed by a DomainStrategy to build prompts.
 * Assembled by ContextBuilderService and passed to strategy methods.
 */
public record DomainContext(
        ContentType contentType,
        String sourceLanguage,
        String targetLanguage,
        String glossaryBlock,
        String characterBlock,
        String sceneContext,
        String styleGuide,
        String styleExamplesBlock,
        String terminologyBlock,
        String themeBlock,
        String translationMemoryBlock,
        String textToTranslate
) {}
