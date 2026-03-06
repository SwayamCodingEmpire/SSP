package com.isekai.ssp.domain.strategies;

import com.isekai.ssp.domain.*;
import com.isekai.ssp.helpers.ContentFamily;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Strategy for periodical content: magazines and articles.
 * Prioritizes headline impact, journalistic voice, timeliness, and cultural relevance.
 */
@Component
public class PeriodicalStrategy implements DomainStrategy {

    @Override
    public ContentFamily getFamily() {
        return ContentFamily.PERIODICAL;
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
                You are a professional journalist-translator from %s to %s.
                Your ONLY goal in this pass is ACCURACY and COMPLETENESS.

                RULES:
                - Translate every sentence. Miss nothing.
                - Preserve ALL headlines, subheadlines, and pull quotes with their impact.
                - Preserve ALL data, statistics, quotes, and source attributions exactly.
                - Preserve bylines and datelines.
                - Preserve image captions and sidebar text.
                - Use the enforced glossary terms below without deviation.
                - If a current event reference needs context for the target audience,
                  add a [bracketed contextual note].
                - Do NOT editorialize or alter the journalistic angle.

                ## Enforced glossary
                %s
                """.formatted(ctx.sourceLanguage(), ctx.targetLanguage(), ctx.glossaryBlock());
    }

    @Override
    public String buildPass1UserPrompt(DomainContext ctx) {
        return """
                Translate the following article faithfully from %s to %s.
                Preserve every fact, quote, headline, and structural element.
                Output only the translated text — no commentary.

                ## Text:
                %s
                """.formatted(ctx.sourceLanguage(), ctx.targetLanguage(), ctx.textToTranslate());
    }

    @Override
    public String buildPass2SystemPrompt(DomainContext ctx) {
        return """
                You are a senior magazine editor and translator working from %s to %s.
                You have a faithful draft. Your task is to make it read like native journalism —
                punchy, engaging, and culturally relevant.

                ## Periodical translation principles
                - HEADLINE IMPACT: Headlines must grab attention in the target language.
                  Translate the intent, not the words. A weak literal headline is a failure.
                - LEAD PARAGRAPH: The opening must hook the reader. If the source lead works
                  in the target culture, keep it. If not, adapt it.
                - JOURNALISTIC VOICE: Match the source's editorial voice — hard news stays neutral,
                  features stay narrative, opinion pieces stay persuasive.
                - CULTURAL RELEVANCE: Adapt cultural references so the target reader relates.
                  A reference obscure to the target audience should be replaced with an
                  equivalent that carries the same meaning.
                - TIMELINESS: If the article references current events, ensure the context is clear
                  for the target audience without over-explaining.
                - PULL QUOTES: Select the most impactful quote for pull-quote positioning.
                - CAPTIONS: Image captions must be punchy and informative.

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
                ## Source article (original %s):
                %s

                ## Faithful draft:
                %s

                Polish into publication-ready %s journalism.
                Make headlines punch, leads hook, and the voice match the source's editorial style.
                Provide contextNotes on cultural adaptations and headline choices.
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
        return List.of("HEADLINE", "SUBHEAD", "LEAD", "BODY", "PULL_QUOTE",
                "SIDEBAR", "CAPTION", "BYLINE");
    }

    @Override
    public List<String> getGlossaryCategories() {
        return List.of("PROPER_NOUN", "ORGANIZATION", "CULTURAL_REFERENCE",
                "IDIOM", "FOREIGN_WORD", "TITLE");
    }

    @Override
    public List<QualityDimension> getQualityDimensions() {
        return List.of(
                QualityDimension.ACCURACY,
                QualityDimension.FLUENCY,
                QualityDimension.STYLE,
                QualityDimension.CULTURAL_ADAPTATION
        );
    }

    private String buildStyleSection(String styleGuide) {
        if (styleGuide == null || styleGuide.isBlank()) return "";
        return "## Publication style guide\n" + styleGuide + "\n";
    }

    private String buildStyleExamplesSection(String styleExamplesBlock) {
        if (styleExamplesBlock == null || styleExamplesBlock.isBlank()) return "";
        return """
                ## Approved translation examples
                Study these for the target publication voice.

                %s

                """.formatted(styleExamplesBlock);
    }
}
