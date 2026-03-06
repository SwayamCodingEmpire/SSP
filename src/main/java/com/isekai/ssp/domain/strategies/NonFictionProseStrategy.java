package com.isekai.ssp.domain.strategies;

import com.isekai.ssp.domain.*;
import com.isekai.ssp.helpers.ContentFamily;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Strategy for non-fiction prose: non-fiction books, essays, general books.
 * Prioritizes argument flow, author voice, factual accuracy, and persuasive tone.
 */
@Component
public class NonFictionProseStrategy implements DomainStrategy {

    @Override
    public ContentFamily getFamily() {
        return ContentFamily.NON_FICTION_PROSE;
    }

    @Override
    public List<AnalysisStep> getAnalysisPipeline() {
        return List.of(
                AnalysisStep.SECTION_ANALYSIS,
                AnalysisStep.THEME_EXTRACTION
        );
    }

    @Override
    public String buildPass1SystemPrompt(DomainContext ctx) {
        return """
                You are a professional non-fiction translator from %s to %s.
                Your ONLY goal in this pass is ACCURACY and COMPLETENESS.

                RULES:
                - Translate every sentence. Miss nothing.
                - Preserve the author's argumentative structure exactly — thesis, evidence, conclusion.
                - Preserve all data, statistics, dates, and factual claims without alteration.
                - Preserve all quotations attributed to others — translate the quote, keep the attribution.
                - Preserve all footnotes, endnotes, and references.
                - Use the enforced glossary terms below without deviation.
                - If a cultural reference needs explanation for the target audience, add a [bracketed note].
                - Do NOT editorialize, simplify, or restructure the argument.

                ## Enforced glossary
                %s
                """.formatted(ctx.sourceLanguage(), ctx.targetLanguage(), ctx.glossaryBlock());
    }

    @Override
    public String buildPass1UserPrompt(DomainContext ctx) {
        return """
                Translate the following non-fiction text faithfully from %s to %s.
                Preserve every argument, every piece of evidence, every nuance.
                Output only the translated text — no commentary.

                ## Text:
                %s
                """.formatted(ctx.sourceLanguage(), ctx.targetLanguage(), ctx.textToTranslate());
    }

    @Override
    public String buildPass2SystemPrompt(DomainContext ctx) {
        return """
                You are a non-fiction editor-translator working from %s to %s.
                You have a faithful draft. Your task is to elevate it into compelling non-fiction prose
                that carries the author's voice and persuasive power.

                ## Non-fiction translation principles
                - AUTHOR VOICE: Every non-fiction author has a distinctive voice — analytical, passionate,
                  sardonic, meditative. Identify it and preserve it.
                - ARGUMENT FLOW: The logical chain (premise → evidence → conclusion) must be seamless.
                  Improve connectives if the draft reads choppily, but never alter the argument.
                - EVIDENCE INTEGRITY: Data, statistics, quotes, and citations must be EXACT.
                  Never round numbers, paraphrase direct quotes, or modify source attributions.
                - RHETORICAL DEVICES: If the author uses rhetorical questions, anaphora, or other
                  persuasive devices, preserve them — they're deliberate.
                - CULTURAL BRIDGING: Adapt cultural references when needed for the target audience,
                  but always in a way that preserves the author's point.
                - REGISTER: Match the source's formality level. Popular science stays accessible.
                  Academic non-fiction stays formal. Memoir stays personal.

                %s
                ## Enforced glossary (never deviate from these terms)
                %s
                %s
                Respond ONLY with valid JSON matching the requested format.
                """.formatted(
                ctx.sourceLanguage(), ctx.targetLanguage(),
                buildStyleSection(ctx.styleGuide()),
                ctx.glossaryBlock(),
                buildStyleExamplesSection(ctx.styleExamplesBlock()));
    }

    @Override
    public String buildPass2UserPrompt(DomainContext ctx, String faithfulDraft) {
        return """
                ## Source text (original %s — for author's voice and argumentative intent):
                %s

                ## Faithful draft:
                %s

                Elevate the draft into compelling non-fiction prose in %s.
                Preserve the author's voice and argumentative flow.
                Provide contextNotes on significant editorial or cultural adaptation choices.
                """.formatted(
                ctx.sourceLanguage(), ctx.textToTranslate(),
                faithfulDraft,
                ctx.targetLanguage());
    }

    @Override
    public Set<ContextType> getRequiredContextTypes() {
        return Set.of(
                ContextType.GLOSSARY,
                ContextType.THEMES,
                ContextType.STYLE_EXAMPLES,
                ContextType.TRANSLATION_MEMORY
        );
    }

    @Override
    public List<String> getContentElementTypes() {
        return List.of("INTRODUCTION", "ARGUMENT", "EVIDENCE", "ANECDOTE", "ANALYSIS",
                "CONCLUSION", "SIDEBAR", "CASE_STUDY");
    }

    @Override
    public List<String> getGlossaryCategories() {
        return List.of("PROPER_NOUN", "CONCEPT", "ORGANIZATION", "CULTURAL_REFERENCE",
                "IDIOM", "FOREIGN_WORD", "TITLE");
    }

    @Override
    public List<QualityDimension> getQualityDimensions() {
        return List.of(
                QualityDimension.ACCURACY,
                QualityDimension.FLUENCY,
                QualityDimension.STYLE,
                QualityDimension.VOICE_CONSISTENCY
        );
    }

    private String buildStyleSection(String styleGuide) {
        if (styleGuide == null || styleGuide.isBlank()) return "";
        return "## Project style guide\n" + styleGuide + "\n";
    }

    private String buildStyleExamplesSection(String styleExamplesBlock) {
        if (styleExamplesBlock == null || styleExamplesBlock.isBlank()) return "";
        return """
                ## Approved translation examples
                Study these to understand the target voice.

                %s

                """.formatted(styleExamplesBlock);
    }
}
