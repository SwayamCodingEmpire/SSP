package com.isekai.ssp.service;

import com.isekai.ssp.domain.AnalysisStep;
import com.isekai.ssp.domain.DomainStrategy;
import com.isekai.ssp.domain.DomainStrategyRegistry;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.Character;
import com.isekai.ssp.helpers.AnalysisStatus;
import com.isekai.ssp.helpers.ContentType;
import com.isekai.ssp.repository.ChapterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates the full AI analysis pipeline for a chapter.
 * Uses the DomainStrategyRegistry to determine which analysis steps to run
 * based on the project's content type.
 */
@Service
public class ChapterAnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChapterAnalysisOrchestrator.class);

    private final ChapterRepository chapterRepository;
    private final DomainStrategyRegistry strategyRegistry;
    private final CharacterExtractionService characterExtractionService;
    private final SpeakerDetectionService speakerDetectionService;
    private final SceneAnalysisService sceneAnalysisService;
    private final SectionAnalysisService sectionAnalysisService;
    private final FormAnalysisService formAnalysisService;
    private final DialogueAnalysisService dialogueAnalysisService;
    private final ThemeExtractionService themeExtractionService;
    private final TerminologyExtractionService terminologyExtractionService;

    public ChapterAnalysisOrchestrator(
            ChapterRepository chapterRepository,
            DomainStrategyRegistry strategyRegistry,
            CharacterExtractionService characterExtractionService,
            SpeakerDetectionService speakerDetectionService,
            SceneAnalysisService sceneAnalysisService,
            SectionAnalysisService sectionAnalysisService,
            FormAnalysisService formAnalysisService,
            DialogueAnalysisService dialogueAnalysisService,
            ThemeExtractionService themeExtractionService,
            TerminologyExtractionService terminologyExtractionService) {
        this.chapterRepository = chapterRepository;
        this.strategyRegistry = strategyRegistry;
        this.characterExtractionService = characterExtractionService;
        this.speakerDetectionService = speakerDetectionService;
        this.sceneAnalysisService = sceneAnalysisService;
        this.sectionAnalysisService = sectionAnalysisService;
        this.formAnalysisService = formAnalysisService;
        this.dialogueAnalysisService = dialogueAnalysisService;
        this.themeExtractionService = themeExtractionService;
        this.terminologyExtractionService = terminologyExtractionService;
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<Void> analyzeChapterAsync(Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

        ContentType contentType = chapter.getProject().getContentType();
        DomainStrategy strategy = strategyRegistry.resolve(contentType);
        List<AnalysisStep> pipeline = strategy.getAnalysisPipeline();

        log.info("Starting AI analysis for chapter {} (id={}) with {} pipeline: {}",
                chapter.getChapterNumber(), chapterId, strategy.getFamily(), pipeline);
        chapter.setAnalysisStatus(AnalysisStatus.ANALYZING);
        chapterRepository.save(chapter);

        try {
            int step = 0;
            int total = pipeline.size();

            for (AnalysisStep analysisStep : pipeline) {
                step++;
                log.info("Step {}/{}: {}...", step, total, analysisStep);
                executeStep(analysisStep, chapter);
            }

            chapter.setAnalysisStatus(AnalysisStatus.ANALYZED);
            chapter.setUpdatedAt(LocalDateTime.now());
            chapterRepository.save(chapter);

            log.info("AI analysis complete for chapter {}", chapterId);

        } catch (AiServiceException e) {
            log.error("AI analysis failed for chapter {}: {}", chapterId, e.getMessage(), e);
            chapter.setAnalysisStatus(AnalysisStatus.FAILED);
            chapter.setUpdatedAt(LocalDateTime.now());
            chapterRepository.save(chapter);
            throw e;
        }

        return CompletableFuture.completedFuture(null);
    }

    private void executeStep(AnalysisStep step, Chapter chapter) {
        switch (step) {
            case CHARACTER_EXTRACTION -> {
                List<Character> characters = characterExtractionService.extractCharacters(chapter);
                log.info("Found {} characters", characters.size());
            }
            case SPEAKER_DETECTION -> {
                speakerDetectionService.detectSpeakers(chapter);
                log.info("Speaker detection complete");
            }
            case SCENE_ANALYSIS -> {
                var scenes = sceneAnalysisService.analyzeScenes(chapter);
                log.info("Detected {} scenes", scenes.size());
            }
            case SECTION_ANALYSIS -> {
                var sections = sectionAnalysisService.analyzeSections(chapter);
                log.info("Detected {} sections", sections.size());
            }
            case FORM_ANALYSIS -> {
                formAnalysisService.analyzeForm(chapter);
                log.info("Form analysis complete");
            }
            case DIALOGUE_ANALYSIS -> {
                var elements = dialogueAnalysisService.analyzeDialogue(chapter);
                log.info("Detected {} script elements", elements.size());
            }
            case THEME_EXTRACTION -> {
                var themes = themeExtractionService.extractThemes(chapter);
                log.info("Extracted {} thematic elements", themes.size());
            }
            case TERMINOLOGY_EXTRACTION -> {
                var terms = terminologyExtractionService.extractTerminology(chapter);
                log.info("Extracted {} domain terms", terms.size());
            }
        }
    }
}
