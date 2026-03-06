package com.isekai.ssp.domain.strategies;

import com.isekai.ssp.domain.*;
import com.isekai.ssp.helpers.ContentFamily;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Strategy for narrative fiction: novels, short stories, classic/modern literature.
 * Extracted from the original hardcoded TranslationService prompts.
 * Prioritizes character voice, emotional arc, and literary style.
 */
@Component
public class NarrativeFictionStrategy implements DomainStrategy {

    @Override
    public ContentFamily getFamily() {
        return ContentFamily.NARRATIVE_FICTION;
    }

    @Override
    public List<AnalysisStep> getAnalysisPipeline() {
        return List.of(
                AnalysisStep.CHARACTER_EXTRACTION,
                AnalysisStep.SPEAKER_DETECTION,
                AnalysisStep.SCENE_ANALYSIS
        );
    }

    @Override
    public String buildPass1SystemPrompt(DomainContext ctx) {
        return """
                You are a professional literary translator from %s to %s.
                Your ONLY goal in this pass is ACCURACY and COMPLETENESS.

                RULES:
                - Translate every sentence. Miss nothing.
                - Preserve all cultural references — keep them or add a bracketed note, never remove.
                - Preserve character voice and dialogue registers exactly as in the source.
                - Use the enforced glossary terms below without any deviation.
                - Do NOT beautify, embellish, or restructure for style. That comes in the next pass.
                - If a concept has no direct equivalent, transliterate and add a brief [bracketed note].

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
                Translate the following text faithfully from %s to %s.
                Preserve every sentence, every detail, every nuance of meaning.
                Output only the translated text — no commentary, no formatting markers.

                ## Text:
                %s
                """.formatted(ctx.sourceLanguage(), ctx.targetLanguage(), ctx.textToTranslate());
    }

    @Override
    public String buildPass2SystemPrompt(DomainContext ctx) {
        return """
                You are a literary translator and co-author, working in creative partnership with the original author.
                You translate from %s to %s.

                Your goal is EFFECT EQUIVALENCE — the reader of your translation should feel exactly what
                the reader of the original felt. This is not word-for-word substitution. It is re-expression
                in the target language by a skilled native author who deeply respects the source.

                ## Your creative license — the professional translator's toolkit
                - ADAPTATION:    Replace cultural elements with target-culture equivalents when it improves resonance.
                - AMPLIFICATION: Add linguistic richness to explain concepts with no direct equivalent.
                - COMPENSATION:  Move a stylistic effect to a different sentence position if it works better there.
                - ELISION:       Remove elements that clutter in translation without losing meaning.
                - BORROWING:     Keep source-language words when their foreignness adds authenticity.

                ## Prose craft — these are non-negotiable
                - Vary sentence length deliberately. Short. Punchy. Then a long sentence that builds and
                  breathes and carries the reader forward with it. Monotonous length kills prose.
                - Fragment for impact when the emotion demands it.
                - Ground abstract emotions in physical sensation — hunger is a pull in the gut,
                  grief is weight behind the eyes, anticipation is a tightening in the chest.
                - Use repetition and anaphora when the source has intensity ("A hunger. A hunger so
                  vast it eclipsed thought, eclipsed reason, eclipsed everything but itself.").
                - Dialogue must carry the speaker's register exactly: formal, clipped, lyrical, or
                  crude as they are — voice is character.
                - Read your output aloud mentally. If it doesn't breathe, rewrite it.

                ## Interior monologue and ambiguous states — CRITICAL
                Translate the EXPERIENCE, not the words. When a character's inner state is ambiguous,
                contradictory, or self-referential — do not render it mechanically.
                The character's mind is the canvas. Capture its texture.

                %s
                %s
                ## Enforced glossary (never deviate from these terms)
                %s

                ## Characters
                %s
                %s
                Respond ONLY with valid JSON matching the requested format.
                """.formatted(
                ctx.sourceLanguage(), ctx.targetLanguage(),
                buildSceneGuidance(ctx.sceneContext()),
                buildStyleSection(ctx.styleGuide()),
                ctx.glossaryBlock(), ctx.characterBlock(),
                buildStyleExamplesSection(ctx.styleExamplesBlock()));
    }

    @Override
    public String buildPass2UserPrompt(DomainContext ctx, String faithfulDraft) {
        String sceneHeader = (ctx.sceneContext() != null && !ctx.sceneContext().isBlank())
                ? "## Active scene context:\n" + ctx.sceneContext() + "\n\n"
                : "";
        return """
                %s\
                ## Source text (original %s — for author intent and emotional register):
                %s

                ## Faithful draft (your raw material — accurate but unpolished):
                %s

                Rewrite the faithful draft as literary prose in %s.
                Let the scene context shape every sentence — wrong texture for the scene is as bad as a wrong word.
                Provide brief contextNotes on any significant creative choices (restructuring, cultural adaptation, etc.).
                """.formatted(
                sceneHeader,
                ctx.sourceLanguage(), ctx.textToTranslate(),
                faithfulDraft,
                ctx.targetLanguage());
    }

    @Override
    public Set<ContextType> getRequiredContextTypes() {
        return Set.of(
                ContextType.CHARACTERS,
                ContextType.GLOSSARY,
                ContextType.STYLE_EXAMPLES,
                ContextType.SCENES
        );
    }

    @Override
    public List<String> getContentElementTypes() {
        return List.of("DIALOGUE", "ACTION", "BATTLE", "INTROSPECTION", "ROMANCE", "EXPOSITION", "TRANSITION");
    }

    @Override
    public List<String> getGlossaryCategories() {
        return List.of("CHARACTER_NAME", "LOCATION", "MAGIC_SPELL", "ITEM", "CONCEPT", "ORGANIZATION", "TITLE");
    }

    @Override
    public List<QualityDimension> getQualityDimensions() {
        return List.of(
                QualityDimension.ACCURACY,
                QualityDimension.FLUENCY,
                QualityDimension.STYLE,
                QualityDimension.VOICE_CONSISTENCY,
                QualityDimension.CULTURAL_ADAPTATION
        );
    }

    // -------------------------------------------------------------------------
    // Prompt helpers (extracted from original TranslationService)
    // -------------------------------------------------------------------------

    private String buildSceneGuidance(String sceneContext) {
        if (sceneContext == null || sceneContext.isBlank()) return "";
        return """
                ## Scene context — let this shape every sentence
                %s

                Scene-type music guide (match the prose texture to the scene):
                - BATTLE / ACTION   → Fragment. Compress. Short declarative strikes. Kinetic verbs. No breathing room.
                - INTROSPECTION     → Stream-of-thought. Recursive, circling. Interior rawness. Allow the mind to wander and double back.
                - ROMANCE           → Slow the rhythm. Sensory weight on every detail. Let the unsaid carry as much as the said.
                - DIALOGUE          → Subtext beneath every line. Characters often speak past each other. Silence is a line of dialogue.
                - EXPOSITION        → Texture over bare description. Anchor the world through sensory detail, not catalogue.
                - TRANSITION        → Sparse and atmospheric. Let whitespace breathe. Bridge with image, not summary.

                Tone modifiers:
                - TENSE             → Physicalize dread. Short sentences. Held breath. The body knows before the mind.
                - MELANCHOLIC       → Long, trailing sentences. Quiet beauty. Let sorrow linger at the end of a line.
                - TRIUMPHANT        → Build. Rise. Let rhythm crescendo to the peak.
                - MYSTERIOUS        → Withhold. Fragment deliberately. Let the gaps do the work.
                - HUMOROUS          → Light touch. Timing is everything — rhythm is the punchline.
                - SERIOUS           → Weight and economy. Every word earns its place.
                """.formatted(sceneContext);
    }

    private String buildStyleExamplesSection(String styleExamplesBlock) {
        if (styleExamplesBlock == null || styleExamplesBlock.isBlank()) return "";
        return """
                ## Approved translation examples for this scene type
                Study these approved translations to understand the target prose style.
                They show exactly how literary elevation should look for this project.

                %s

                """.formatted(styleExamplesBlock);
    }

    private String buildStyleSection(String styleGuide) {
        if (styleGuide == null || styleGuide.isBlank()) return "";
        return "## Project prose style\n" + styleGuide + "\n";
    }
}
