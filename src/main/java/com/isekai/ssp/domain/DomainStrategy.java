package com.isekai.ssp.domain;

import com.isekai.ssp.helpers.ContentFamily;

import java.util.List;
import java.util.Set;

/**
 * Strategy interface for domain-specific translation behavior.
 * One implementation per ContentFamily. Each strategy defines:
 * - Which analysis steps to run (and in what order)
 * - How to build Pass 1 and Pass 2 translation prompts
 * - What RAG context types to retrieve
 * - Domain-specific content element types, glossary categories, and quality dimensions
 */
public interface DomainStrategy {

    ContentFamily getFamily();

    /** Which analysis steps to run (in order). */
    List<AnalysisStep> getAnalysisPipeline();

    /** Build Pass 1 (faithful draft) system prompt. */
    String buildPass1SystemPrompt(DomainContext context);

    /** Build Pass 1 user prompt. */
    String buildPass1UserPrompt(DomainContext context);

    /** Build Pass 2 (elevation/refinement) system prompt. */
    String buildPass2SystemPrompt(DomainContext context);

    /** Build Pass 2 user prompt, given the Pass 1 faithful draft. */
    String buildPass2UserPrompt(DomainContext context, String faithfulDraft);

    /** What RAG context types to retrieve for translation. */
    Set<ContextType> getRequiredContextTypes();

    /** Domain-specific content element types (e.g. SceneType values for fiction, SectionType for academic). */
    List<String> getContentElementTypes();

    /** Domain-specific glossary categories. */
    List<String> getGlossaryCategories();

    /** Quality dimensions to evaluate for this domain. */
    List<QualityDimension> getQualityDimensions();
}
