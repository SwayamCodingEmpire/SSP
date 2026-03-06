package com.isekai.ssp.service;

import com.isekai.ssp.domain.DomainContext;
import com.isekai.ssp.domain.DomainStrategy;
import com.isekai.ssp.domain.DomainStrategyRegistry;
import com.isekai.ssp.dto.TranslationResult;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.Segment;
import com.isekai.ssp.helpers.ChapterStatus;
import com.isekai.ssp.helpers.ContentType;
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
 * Translates chapters using a two-pass pipeline, delegating prompt construction
 * to the appropriate DomainStrategy based on the project's content type.
 *
 *   Pass 1 — Faithful draft:   accurate, meaning-preserving, domain-appropriate.
 *   Pass 2 — Elevation/refinement: domain-specific quality improvement.
 */
@Service
public class TranslationService {

    private static final int MAX_CHAPTER_CHARS = 8000;
    private static final Logger logger = LoggerFactory.getLogger(TranslationService.class);

    private final LlmProviderRegistry providerRegistry;
    private final DomainStrategyRegistry strategyRegistry;
    private final SegmentRepository segmentRepository;
    private final ChapterRepository chapterRepository;
    private final ContextBuilderService contextBuilder;

    @Value("${ssp.ai.translation.two-pass:true}")
    private boolean twoPassEnabled;

    public TranslationService(
            LlmProviderRegistry providerRegistry,
            DomainStrategyRegistry strategyRegistry,
            SegmentRepository segmentRepository,
            ChapterRepository chapterRepository,
            ContextBuilderService contextBuilder) {
        this.providerRegistry = providerRegistry;
        this.strategyRegistry = strategyRegistry;
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
            ContentType contentType = chapter.getProject().getContentType();
            DomainStrategy strategy = strategyRegistry.resolve(contentType);
            String text = chapter.getOriginalText();

            logger.info("Translating chapter {} with provider={}, strategy={}, two-pass={}",
                    chapterId, provider.getName(), strategy.getFamily(), twoPassEnabled);

            if (text.length() <= MAX_CHAPTER_CHARS) {
                chapter.setTranslatedText(translateText(chapter, text, provider, strategy));
                chapter.setChunked(false);
            } else {
                chapter.setChunked(true);
                List<Segment> segments = splitIntoSegments(text, MAX_CHAPTER_CHARS, chapter);
                segmentRepository.saveAll(segments);
                chapter.setTotalSegments(segments.size());

                for (Segment seg : segments) {
                    seg.setTranslatedText(translateText(chapter, seg.getOriginalText(), provider, strategy));
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
    protected String translateText(Chapter chapter, String text, LlmProvider provider, DomainStrategy strategy) {
        DomainContext ctx = contextBuilder.buildDomainContext(chapter, text, strategy);
        return twoPassEnabled ? twoPassTranslation(ctx, provider, strategy) : singlePassTranslation(ctx, provider, strategy);
    }

    private String twoPassTranslation(DomainContext ctx, LlmProvider provider, DomainStrategy strategy) {
        logger.debug("Pass 1: faithful draft");
        String faithfulDraft = provider.generate(
                strategy.buildPass1SystemPrompt(ctx),
                strategy.buildPass1UserPrompt(ctx)
        );

        logger.debug("Pass 2: domain-specific elevation");
        BeanOutputConverter<TranslationResult> converter = new BeanOutputConverter<>(TranslationResult.class);
        String response = provider.generate(
                strategy.buildPass2SystemPrompt(ctx),
                strategy.buildPass2UserPrompt(ctx, faithfulDraft) + "\n\n" + converter.getFormat()
        );
        return converter.convert(response).translatedText();
    }

    private String singlePassTranslation(DomainContext ctx, LlmProvider provider, DomainStrategy strategy) {
        BeanOutputConverter<TranslationResult> converter = new BeanOutputConverter<>(TranslationResult.class);
        String response = provider.generate(
                strategy.buildPass2SystemPrompt(ctx),
                strategy.buildPass2UserPrompt(ctx, ctx.textToTranslate()) + "\n\n" + converter.getFormat()
        );
        return converter.convert(response).translatedText();
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
