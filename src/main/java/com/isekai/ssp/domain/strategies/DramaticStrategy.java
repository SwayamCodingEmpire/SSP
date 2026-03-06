package com.isekai.ssp.domain.strategies;

import com.isekai.ssp.domain.*;
import com.isekai.ssp.helpers.ContentFamily;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Strategy for TV/movie scripts.
 * Prioritizes dialogue naturalness, speakability, timing, subtext preservation,
 * and accurate stage direction translation.
 */
@Component
public class DramaticStrategy implements DomainStrategy {

    @Override
    public ContentFamily getFamily() {
        return ContentFamily.DRAMATIC;
    }

    @Override
    public List<AnalysisStep> getAnalysisPipeline() {
        return List.of(
                AnalysisStep.CHARACTER_EXTRACTION,
                AnalysisStep.DIALOGUE_ANALYSIS
        );
    }

    @Override
    public String buildPass1SystemPrompt(DomainContext ctx) {
        return """
                You are a professional script translator from %s to %s,
                specializing in TV and film dialogue.
                Your ONLY goal in this pass is ACCURACY and COMPLETENESS.

                RULES:
                - Translate every line of dialogue faithfully. Miss nothing.
                - Preserve ALL formatting conventions:
                  - Scene headings (INT./EXT.) — translate location, keep INT./EXT. markers.
                  - Character cues (CHARACTER NAME) — keep names in original or transliterated form.
                  - Parentheticals (beat), (sotto voce), (O.S.), (V.O.) — translate emotional parentheticals,
                    keep technical ones (O.S., V.O., CONT'D) in standard target-language equivalents.
                  - Transitions (CUT TO:, FADE IN:) — use standard target-language equivalents.
                - Preserve subtext — if a character says one thing and means another, the translation
                  must carry the same gap between surface and meaning.
                - Use the enforced glossary terms below without deviation.
                - Do NOT restructure dialogue or stage directions.

                ## Enforced glossary
                %s

                ## Characters
                %s
                """.formatted(
                ctx.sourceLanguage(), ctx.targetLanguage(),
                ctx.glossaryBlock(), ctx.characterBlock());
    }

    @Override
    public String buildPass1UserPrompt(DomainContext ctx) {
        return """
                Translate the following script faithfully from %s to %s.
                Preserve every line, every stage direction, every formatting convention.
                Output only the translated script — no commentary.

                ## Text:
                %s
                """.formatted(ctx.sourceLanguage(), ctx.targetLanguage(), ctx.textToTranslate());
    }

    @Override
    public String buildPass2SystemPrompt(DomainContext ctx) {
        return """
                You are a script localizer and dialogue specialist, adapting from %s to %s.
                You have a faithful draft. Your task is to make the dialogue COME ALIVE —
                characters must sound like real people speaking in %s.

                ## Dialogue naturalization principles
                - SPEAKABILITY: Read every line aloud. If it sounds written, not spoken, rewrite it.
                  Real speech has contractions, interruptions, half-thoughts, and verbal tics.
                - SUBTEXT: The meaning beneath the words is more important than the words themselves.
                  Preserve what characters AREN'T saying as carefully as what they are.
                - CHARACTER VOICE: Each character has a distinct speech pattern. Formal characters stay formal.
                  Street-smart characters use colloquial language. Don't homogenize voices.
                - TIMING: Dialogue needs rhythm. Short exchanges should stay punchy. Long monologues
                  should breathe. Match the pacing of the original.
                - LIP-SYNC AWARENESS (for dubbing): Where possible, match the mouth movements of
                  key words, especially at line starts and on emphasized syllables.
                - CULTURAL HUMOR: Jokes, references, and wordplay must be adapted to land in the
                  target culture. A translated joke that doesn't land is worse than no joke.
                - STAGE DIRECTIONS: Translate precisely but naturally. Action lines should be vivid
                  and present-tense. Camera directions stay technical.

                %s
                ## Enforced glossary (never deviate from these terms)
                %s

                ## Characters
                %s
                %s
                Respond ONLY with valid JSON matching the requested format.
                """.formatted(
                ctx.sourceLanguage(), ctx.targetLanguage(), ctx.targetLanguage(),
                buildStyleSection(ctx.styleGuide()),
                ctx.glossaryBlock(), ctx.characterBlock(),
                buildStyleExamplesSection(ctx.styleExamplesBlock()));
    }

    @Override
    public String buildPass2UserPrompt(DomainContext ctx, String faithfulDraft) {
        return """
                ## Source script (original %s):
                %s

                ## Faithful draft:
                %s

                Naturalize the dialogue in %s. Make every character sound like a real person.
                Preserve all script formatting conventions.
                Provide contextNotes on significant adaptation choices (humor, cultural references, lip-sync adjustments).
                """.formatted(
                ctx.sourceLanguage(), ctx.textToTranslate(),
                faithfulDraft,
                ctx.targetLanguage());
    }

    @Override
    public Set<ContextType> getRequiredContextTypes() {
        return Set.of(
                ContextType.CHARACTERS,
                ContextType.GLOSSARY,
                ContextType.STYLE_EXAMPLES
        );
    }

    @Override
    public List<String> getContentElementTypes() {
        return List.of("SCENE_HEADING", "ACTION_LINE", "CHARACTER_CUE", "DIALOGUE",
                "PARENTHETICAL", "TRANSITION", "MONTAGE");
    }

    @Override
    public List<String> getGlossaryCategories() {
        return List.of("CHARACTER_NAME", "STAGE_DIRECTION_TERM", "INDUSTRY_JARGON",
                "LOCATION", "PROPER_NOUN", "CULTURAL_REFERENCE", "IDIOM");
    }

    @Override
    public List<QualityDimension> getQualityDimensions() {
        return List.of(
                QualityDimension.ACCURACY,
                QualityDimension.FLUENCY,
                QualityDimension.VOICE_CONSISTENCY,
                QualityDimension.CULTURAL_ADAPTATION
        );
    }

    private String buildStyleSection(String styleGuide) {
        if (styleGuide == null || styleGuide.isBlank()) return "";
        return "## Project script style\n" + styleGuide + "\n";
    }

    private String buildStyleExamplesSection(String styleExamplesBlock) {
        if (styleExamplesBlock == null || styleExamplesBlock.isBlank()) return "";
        return """
                ## Approved translation examples
                Study these to understand the target dialogue style.

                %s

                """.formatted(styleExamplesBlock);
    }
}
