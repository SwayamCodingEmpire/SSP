package com.isekai.ssp.service;

import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.Character;
import com.isekai.ssp.helpers.AnalysisStatus;
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
 * Runs asynchronously: character extraction → speaker detection → scene analysis.
 * Order matters — speaker detection depends on characters being extracted first.
 */
@Service
public class ChapterAnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChapterAnalysisOrchestrator.class);

    private final ChapterRepository chapterRepository;
    private final CharacterExtractionService characterExtractionService;
    private final SpeakerDetectionService speakerDetectionService;
    private final SceneAnalysisService sceneAnalysisService;

    public ChapterAnalysisOrchestrator(
            ChapterRepository chapterRepository,
            CharacterExtractionService characterExtractionService,
            SpeakerDetectionService speakerDetectionService,
            SceneAnalysisService sceneAnalysisService) {
        this.chapterRepository = chapterRepository;
        this.characterExtractionService = characterExtractionService;
        this.speakerDetectionService = speakerDetectionService;
        this.sceneAnalysisService = sceneAnalysisService;
    }

    /**
     * Runs the full analysis pipeline asynchronously.
     */
    @Async("aiTaskExecutor")
    public CompletableFuture<Void> analyzeChapterAsync(Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

        log.info("Starting AI analysis for chapter {} (id={})", chapter.getChapterNumber(), chapterId);
        chapter.setAnalysisStatus(AnalysisStatus.ANALYZING);
        chapterRepository.save(chapter);

        try {
            // Step 1: Extract characters (must run first)
            log.info("Step 1/3: Extracting characters...");
            List<Character> characters = characterExtractionService.extractCharacters(chapter);
            log.info("Found {} characters", characters.size());

            // Step 2: Detect speakers (depends on characters)
            log.info("Step 2/3: Detecting speakers...");
            speakerDetectionService.detectSpeakers(chapter);
            log.info("Speaker detection complete");

            // Step 3: Analyze scenes (benefits from speaker info)
            log.info("Step 3/3: Analyzing scenes...");
            var scenes = sceneAnalysisService.analyzeScenes(chapter);
            log.info("Detected {} scenes", scenes.size());

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
}
