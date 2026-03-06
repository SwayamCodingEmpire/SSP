package com.isekai.ssp.domain.strategies;

import com.isekai.ssp.domain.*;
import com.isekai.ssp.helpers.ContentFamily;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Strategy for poetry and song lyrics.
 * Prioritizes form preservation (meter, rhyme, stanza structure), emotional resonance,
 * and musicality. Song lyrics add syllable count and singability constraints.
 */
@Component
public class PoeticStrategy implements DomainStrategy {

    @Override
    public ContentFamily getFamily() {
        return ContentFamily.POETIC;
    }

    @Override
    public List<AnalysisStep> getAnalysisPipeline() {
        return List.of(
                AnalysisStep.FORM_ANALYSIS,
                AnalysisStep.THEME_EXTRACTION
        );
    }

    @Override
    public String buildPass1SystemPrompt(DomainContext ctx) {
        return """
                You are a professional literary translator specializing in poetry, translating from %s to %s.
                Your goal in this pass is a LITERAL MEANING PRESERVATION with form annotations.

                RULES:
                - Translate the MEANING of every line accurately.
                - After each stanza, annotate in brackets:
                  [Form: meter pattern, rhyme scheme, notable sound devices (alliteration, assonance, etc.)]
                - Preserve all imagery and symbolism — translate the image, not a paraphrase of it.
                - Preserve line breaks as they appear in the source.
                - Use the enforced glossary terms below without deviation.
                - Mark any untranslatable wordplay or puns with [wordplay: explanation].
                - Do NOT attempt to rhyme or match meter in this pass — that comes in the next pass.

                ## Enforced glossary
                %s
                """.formatted(ctx.sourceLanguage(), ctx.targetLanguage(), ctx.glossaryBlock());
    }

    @Override
    public String buildPass1UserPrompt(DomainContext ctx) {
        return """
                Translate the following poetry faithfully from %s to %s.
                Preserve every image, every symbol, every line.
                Annotate form elements (meter, rhyme, sound devices) after each stanza.
                Output the translated text with annotations — no other commentary.

                ## Text:
                %s
                """.formatted(ctx.sourceLanguage(), ctx.targetLanguage(), ctx.textToTranslate());
    }

    @Override
    public String buildPass2SystemPrompt(DomainContext ctx) {
        return """
                You are a poet-translator working from %s to %s.
                You have a faithful literal draft with form annotations. Your task is POETIC RECONSTRUCTION —
                recreate the poem in the target language as a living work of art.

                ## Poetic translation principles
                - EMOTIONAL RESONANCE over literal accuracy. The reader must FEEL what the original evokes.
                - FORM PRESERVATION hierarchy (attempt in order, sacrifice later items first):
                  1. Stanza structure and line count
                  2. Core imagery and symbolism
                  3. Meter/rhythm pattern
                  4. Rhyme scheme
                  5. Sound devices (alliteration, assonance)
                - When form and meaning conflict: preserve meaning for the key lines (turns, climax, closing).
                  Sacrifice form on connective/transitional lines.
                - RHYME OPTIONS: If the source rhymes, try to rhyme in the target. If a perfect rhyme
                  forces awkward phrasing, prefer near-rhyme or assonance over forced exact rhyme.
                - METER: Match the overall rhythmic feel (iambic → iambic equivalent in target), not
                  the exact syllable count per line.
                - ENJAMBMENT: Preserve deliberate enjambment. If the source breaks a phrase across lines
                  for emphasis, do the same.
                - LINE ENDINGS: Terminal words carry extra weight in poetry. Choose line-ending words
                  for maximum impact.

                ## Song lyrics additional rules (if content type is SONG_LYRICS)
                - SINGABILITY is paramount — the translation must fit the melody's rhythm.
                - Match syllable count as closely as possible to the source.
                - Stressed syllables in the translation must fall on musical beats.
                - Preserve chorus repetition exactly — the chorus must be memorable and consistent.
                - Vowel sounds on held notes should be open (ah, oh, ee) not closed (uh, ih).

                %s
                ## Enforced glossary
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
                ## Source poem (original %s):
                %s

                ## Literal draft with form annotations:
                %s

                Reconstruct this as a living poem in %s.
                Prioritize emotional resonance and form preservation.
                Provide contextNotes on form decisions: what was preserved, what was sacrificed, and why.
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
                ContextType.STYLE_EXAMPLES
        );
    }

    @Override
    public List<String> getContentElementTypes() {
        return List.of("SONNET", "HAIKU", "FREE_VERSE", "BLANK_VERSE", "LIMERICK",
                "BALLAD", "ODE", "ELEGY", "VILLANELLE", "GHAZAL", "CUSTOM");
    }

    @Override
    public List<String> getGlossaryCategories() {
        return List.of("ARCHAIC_WORD", "POETIC_DEVICE", "ALLUSION", "LITERARY_REFERENCE",
                "FOREIGN_WORD", "CULTURAL_REFERENCE");
    }

    @Override
    public List<QualityDimension> getQualityDimensions() {
        return List.of(
                QualityDimension.ACCURACY,
                QualityDimension.FORM_PRESERVATION,
                QualityDimension.STYLE,
                QualityDimension.CULTURAL_ADAPTATION
        );
    }

    private String buildStyleSection(String styleGuide) {
        if (styleGuide == null || styleGuide.isBlank()) return "";
        return "## Project poetic style\n" + styleGuide + "\n";
    }

    private String buildStyleExamplesSection(String styleExamplesBlock) {
        if (styleExamplesBlock == null || styleExamplesBlock.isBlank()) return "";
        return """
                ## Approved translation examples
                Study these to understand the target poetic voice for this project.

                %s

                """.formatted(styleExamplesBlock);
    }
}
