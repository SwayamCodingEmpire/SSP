package com.isekai.ssp.service;

import com.isekai.ssp.dto.TranslationResult;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.Segment;
import com.isekai.ssp.helpers.ChapterStatus;
import com.isekai.ssp.helpers.TranslationStatus;
import com.isekai.ssp.llm.LlmProvider;
import com.isekai.ssp.llm.LlmProviderRegistry;
import com.isekai.ssp.repository.ChapterRepository;
import com.isekai.ssp.repository.SegmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Translates chapters using a two-pass literary pipeline:
 *   Pass 1 — Faithful draft:   accurate, meaning-preserving, no style concern.
 *   Pass 2 — Literary elevation: re-expresses the draft as skilled native prose,
 *             shaped by scene context (tone/pace/tension) and project style guide.
 *
 * The provider used for each request is resolved from the API's optional ?provider= param
 * via {@link LlmProviderRegistry}, falling back to ssp.ai.active-provider.
 *
 * Two-pass mode is controlled by ssp.ai.translation.two-pass (default: true).
 */
@Service
public class TranslationService {

    private static final int MAX_CHAPTER_CHARS = 8000;
    private static final Logger logger = LoggerFactory.getLogger(TranslationService.class);

    private final LlmProviderRegistry providerRegistry;
    private final SegmentRepository segmentRepository;
    private final ChapterRepository chapterRepository;
    private final ContextBuilderService contextBuilder;

    @Value("${ssp.ai.translation.two-pass:true}")
    private boolean twoPassEnabled;

    public TranslationService(
            LlmProviderRegistry providerRegistry,
            SegmentRepository segmentRepository,
            ChapterRepository chapterRepository,
            ContextBuilderService contextBuilder) {
        this.providerRegistry = providerRegistry;
        this.segmentRepository = segmentRepository;
        this.chapterRepository = chapterRepository;
        this.contextBuilder = contextBuilder;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public CompletableFuture<Void> translateChapterAsync(Long chapterId, String providerOverride) {
        try {
            Chapter chapter = chapterRepository.findById(chapterId)
                    .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

            chapter.setStatus(ChapterStatus.TRANSLATING);
            chapter.setUpdatedAt(LocalDateTime.now());
            chapterRepository.save(chapter);

            LlmProvider provider = providerRegistry.resolve(providerOverride);
            String text = chapter.getOriginalText();
            logger.info("Translating chapter {} with provider={}, two-pass={}", chapterId, provider.getName(), twoPassEnabled);

            if (text.length() <= MAX_CHAPTER_CHARS) {
                chapter.setTranslatedText(translateText(chapter, text, provider));
                chapter.setChunked(false);
            } else {
                chapter.setChunked(true);
                List<Segment> segments = splitIntoSegments(text, MAX_CHAPTER_CHARS, chapter);
                segmentRepository.saveAll(segments);
                chapter.setTotalSegments(segments.size());

                for (Segment seg : segments) {
                    seg.setTranslatedText(translateText(chapter, seg.getOriginalText(), provider));
                    seg.setStatus(TranslationStatus.AI_TRANSLATED);
                    seg.setTranslatedAt(LocalDateTime.now());
                    segmentRepository.save(seg);
                }

                chapter.setTranslatedText(segments.stream()
                        .map(Segment::getTranslatedText)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining("")));
                chapter.setTranslatedSegments(segments.size());
            }

            chapter.setTranslationStatus(TranslationStatus.AI_TRANSLATED);
            chapter.setStatus(ChapterStatus.COMPLETED);
            chapter.setUpdatedAt(LocalDateTime.now());
            chapterRepository.save(chapter);
            logger.info("Translation complete for chapter {}", chapterId);

        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceException(
                    providerOverride != null ? providerOverride : providerRegistry.getDefaultName(),
                    "translation", "Failed to translate chapter " + chapterId, e);
        }
        return CompletableFuture.completedFuture(null);
    }

    public String getActiveProvider() {
        return providerRegistry.getDefaultName();
    }

    // -------------------------------------------------------------------------
    // Translation pipeline
    // -------------------------------------------------------------------------

    @Transactional
    protected String translateText(Chapter chapter, String text, LlmProvider provider) {
        ContextBuilderService.TranslationContext ctx = contextBuilder.buildTranslationContext(chapter, text);
        return twoPassEnabled ? twoPassTranslation(ctx, provider) : singlePassTranslation(ctx, provider);
    }

    /**
     * Two-pass pipeline — mirrors how professional literary translators work:
     * Pass 1 produces a faithful, accurate draft.
     * Pass 2 elevates it to literary prose shaped by scene context and project style.
     */
    private String twoPassTranslation(ContextBuilderService.TranslationContext ctx, LlmProvider provider) {
        logger.debug("Pass 1: faithful draft");
        String faithfulDraft = provider.generate(
                buildFaithfulSystemPrompt(ctx),
                buildFaithfulUserPrompt(ctx)
        );

        logger.debug("Pass 2: literary elevation");
        BeanOutputConverter<TranslationResult> converter = new BeanOutputConverter<>(TranslationResult.class);
        String response = provider.generate(
                buildLiterarySystemPrompt(ctx),
                buildLiteraryUserPrompt(ctx, faithfulDraft) + "\n\n" + converter.getFormat()
        );
        return converter.convert(response).translatedText();
    }

    /** Single-pass fallback — literary framing without the faithful pre-draft. */
    private String singlePassTranslation(ContextBuilderService.TranslationContext ctx, LlmProvider provider) {
        BeanOutputConverter<TranslationResult> converter = new BeanOutputConverter<>(TranslationResult.class);
        String response = provider.generate(
                buildLiterarySystemPrompt(ctx),
                buildSinglePassUserPrompt(ctx) + "\n\n" + converter.getFormat()
        );
        return converter.convert(response).translatedText();
    }

    // -------------------------------------------------------------------------
    // Pass 1 prompts — accuracy only, no literary ambition
    // -------------------------------------------------------------------------

    private String buildFaithfulSystemPrompt(ContextBuilderService.TranslationContext ctx) {
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

    private String buildFaithfulUserPrompt(ContextBuilderService.TranslationContext ctx) {
        return """
                Translate the following text faithfully from %s to %s.
                Preserve every sentence, every detail, every nuance of meaning.
                Output only the translated text — no commentary, no formatting markers.

                ## Text:
                %s
                """.formatted(ctx.sourceLanguage(), ctx.targetLanguage(), ctx.textToTranslate());
    }

    // -------------------------------------------------------------------------
    // Pass 2 / single-pass prompts — literary co-authorship, scene-aware
    // -------------------------------------------------------------------------

    private String buildLiterarySystemPrompt(ContextBuilderService.TranslationContext ctx) {
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

                WRONG approach (mechanical):
                  "I was intoxicated. With what? I was intoxicated."
                RIGHT approach (experiential):
                  "I was intoxicated. To what, I wonder. But regardless — I was undeniably intoxicated."

                The right translation lets the reader inhabit the confusion, the searching, the realization.
                Interior monologue should feel like thought in motion — recursive, reaching, unresolved.
                When the source text circles back on itself, let the translation circle back too, but with
                rhythm, not mere repetition.

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

    private String buildLiteraryUserPrompt(ContextBuilderService.TranslationContext ctx, String faithfulDraft) {
        String sceneHeader = sceneHeader(ctx);
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

    private String buildSinglePassUserPrompt(ContextBuilderService.TranslationContext ctx) {
        return """
                %s\
                ## Text to translate (from %s to %s):
                %s

                Translate as a literary co-author. Let scene context shape every prose decision.
                Provide brief contextNotes on significant creative choices.
                """.formatted(
                sceneHeader(ctx),
                ctx.sourceLanguage(), ctx.targetLanguage(), ctx.textToTranslate());
    }

    private String sceneHeader(ContextBuilderService.TranslationContext ctx) {
        return (ctx.sceneContext() != null && !ctx.sceneContext().isBlank())
                ? "## Active scene context:\n" + ctx.sceneContext() + "\n\n"
                : "";
    }

    // -------------------------------------------------------------------------
    // Chunking
    // -------------------------------------------------------------------------

    private List<Segment> splitIntoSegments(String text, int maxChunkSize, Chapter chapter) {
        List<Segment> segments = new ArrayList<>();
        int seq = 1;
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + maxChunkSize, text.length());
            if (end < text.length()) {
                int newlinePos = text.lastIndexOf('\n', end);
                if (newlinePos > start) end = newlinePos + 1;
            }

            Segment seg = new Segment();
            seg.setChapter(chapter);
            seg.setSequenceNumber(seq++);
            seg.setOriginalText(text.substring(start, end));
            seg.setStatus(TranslationStatus.PENDING);
            seg.setCreatedAt(LocalDateTime.now());
            segments.add(seg);

            start = end;
        }
        return segments;
    }
}