package com.isekai.ssp.service;

import com.isekai.ssp.domain.DomainStrategy;
import com.isekai.ssp.domain.DomainStrategyRegistry;
import com.isekai.ssp.domain.QualityDimension;
import com.isekai.ssp.dto.QualityAssessmentResult;
import com.isekai.ssp.dto.QualityIssue;
import com.isekai.ssp.dto.QualityReport;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.QualityScore;
import com.isekai.ssp.llm.LlmProvider;
import com.isekai.ssp.llm.LlmProviderRegistry;
import com.isekai.ssp.repository.ChapterRepository;
import com.isekai.ssp.repository.QualityScoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Automated quality estimation pipeline for translated chapters.
 * Uses LLM-based assessment with domain-specific quality dimensions.
 */
@Service
public class QualityEstimationService {

    private static final Logger logger = LoggerFactory.getLogger(QualityEstimationService.class);

    private final LlmProviderRegistry providerRegistry;
    private final DomainStrategyRegistry strategyRegistry;
    private final QualityScoreRepository qualityScoreRepository;
    private final ChapterRepository chapterRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ssp.ai.quality.threshold:0.7}")
    private float qualityThreshold;

    public QualityEstimationService(
            LlmProviderRegistry providerRegistry,
            DomainStrategyRegistry strategyRegistry,
            QualityScoreRepository qualityScoreRepository,
            ChapterRepository chapterRepository) {
        this.providerRegistry = providerRegistry;
        this.strategyRegistry = strategyRegistry;
        this.qualityScoreRepository = qualityScoreRepository;
        this.chapterRepository = chapterRepository;
    }

    /**
     * Run quality assessment on a translated chapter.
     */
    @Async("aiTaskExecutor")
    public void assessChapterQuality(Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

        if (chapter.getOriginalText() == null || chapter.getTranslatedText() == null) {
            logger.warn("Cannot assess quality for chapter {} — missing source or translation", chapterId);
            return;
        }

        DomainStrategy strategy = strategyRegistry.resolve(chapter.getProject().getContentType());
        QualityReport report = assessQuality(
                chapter.getOriginalText(),
                chapter.getTranslatedText(),
                chapter.getProject().getSourceLanguage(),
                chapter.getProject().getTargetLanguage(),
                strategy
        );

        // Persist quality score
        QualityScore score = qualityScoreRepository.findByChapterId(chapterId)
                .orElse(new QualityScore());
        score.setChapter(chapter);
        score.setOverallScore(report.overallScore());
        score.setDimensionScoresJson(toJson(report.dimensionScores()));
        score.setIssuesJson(toJson(report.issues()));
        score.setSummary(report.summary());
        score.setFlaggedForReview(report.overallScore() < qualityThreshold);
        score.setCreatedAt(LocalDateTime.now());
        qualityScoreRepository.save(score);

        logger.info("Quality assessment for chapter {}: score={}, flagged={}",
                chapterId, report.overallScore(), score.getFlaggedForReview());
    }

    /**
     * Get quality report for a chapter (from stored data).
     */
    public Optional<QualityReport> getQualityReport(Long chapterId) {
        return qualityScoreRepository.findByChapterId(chapterId)
                .map(score -> {
                    try {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Float> dimensions = objectMapper.readValue(
                                score.getDimensionScoresJson(),
                                objectMapper.getTypeFactory().constructMapType(
                                        java.util.HashMap.class, String.class, Float.class));
                        List<QualityIssue> issues = objectMapper.readValue(
                                score.getIssuesJson(),
                                objectMapper.getTypeFactory().constructCollectionType(
                                        java.util.ArrayList.class, QualityIssue.class));
                        return new QualityReport(
                                score.getOverallScore(),
                                dimensions,
                                issues,
                                score.getSummary()
                        );
                    } catch (Exception e) {
                        logger.warn("Failed to deserialize quality report for chapter {}", chapterId);
                        return new QualityReport(score.getOverallScore(), java.util.Map.of(),
                                java.util.List.of(), score.getSummary());
                    }
                });
    }

    private QualityReport assessQuality(String source, String target,
                                         String sourceLang, String targetLang,
                                         DomainStrategy strategy) {
        String dimensions = strategy.getQualityDimensions().stream()
                .map(QualityDimension::name)
                .collect(Collectors.joining(", "));

        BeanOutputConverter<QualityAssessmentResult> converter =
                new BeanOutputConverter<>(QualityAssessmentResult.class);

        try {
            LlmProvider provider = providerRegistry.resolve(null);

            String systemPrompt = """
                    You are a professional translation quality assessor.
                    Evaluate the following translation from %s to %s.

                    Score each of these quality dimensions from 0.0 to 1.0:
                    %s

                    Also provide an overall score (0.0 to 1.0) and identify specific issues.

                    Issue severity levels: CRITICAL, MAJOR, MINOR, SUGGESTION
                    Issue types: MISTRANSLATION, OMISSION, ADDITION, TERMINOLOGY_INCONSISTENCY,
                                 GRAMMAR_ERROR, STYLE_MISMATCH, CULTURAL_ERROR, FORMATTING

                    Be fair but rigorous. Professional translation quality is typically 0.7-0.85.
                    Only perfect, publication-ready work scores above 0.9.
                    """.formatted(sourceLang, targetLang, dimensions);

            String userPrompt = """
                    ## Source text (%s):
                    %s

                    ## Translation (%s):
                    %s
                    """.formatted(sourceLang, truncate(source, 3000), targetLang, truncate(target, 3000))
                    + "\n\n" + converter.getFormat();

            String response = provider.generate(systemPrompt, userPrompt);
            QualityAssessmentResult result = converter.convert(response);

            List<QualityIssue> issues = result.issues() != null
                    ? result.issues().stream()
                    .map(i -> new QualityIssue(i.type(), i.severity(), i.location(), i.description(), i.suggestion()))
                    .toList()
                    : List.of();

            return new QualityReport(
                    result.overallScore(),
                    result.dimensionScores() != null ? result.dimensionScores() : java.util.Map.of(),
                    issues,
                    result.summary()
            );

        } catch (Exception e) {
            logger.error("Quality assessment failed: {}", e.getMessage(), e);
            return new QualityReport(0.0f, java.util.Map.of(), List.of(),
                    "Quality assessment failed: " + e.getMessage());
        }
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n[... truncated for QE assessment]";
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
