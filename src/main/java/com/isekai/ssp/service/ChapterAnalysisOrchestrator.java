package com.isekai.ssp.service;

import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.Character;
import com.isekai.ssp.helpers.AnalysisStatus;
import com.isekai.ssp.repository.ChapterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * Orchestrates the full AI analysis pipeline for a chapter.
 * Runs asynchronously: character extraction and scene analysis run in parallel (2 chat calls),
 * followed by an async world state update.
 * Dialogue emotion data is now embedded in character extraction (merged from speaker detection).
 */
@Service
public class ChapterAnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChapterAnalysisOrchestrator.class);

    private final ChapterRepository chapterRepository;
    private final CharacterExtractionService characterExtractionService;
    private final SceneAnalysisService sceneAnalysisService;
    private final WorldStateService worldStateService;
    private final Executor taskExecutor;

    public ChapterAnalysisOrchestrator(
            ChapterRepository chapterRepository,
            CharacterExtractionService characterExtractionService,
            SceneAnalysisService sceneAnalysisService,
            WorldStateService worldStateService,
            @Qualifier("aiTaskExecutor") Executor taskExecutor) {
        this.chapterRepository = chapterRepository;
        this.characterExtractionService = characterExtractionService;
        this.sceneAnalysisService = sceneAnalysisService;
        this.worldStateService = worldStateService;
        this.taskExecutor = taskExecutor;
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
            // Step 1+2 (parallel): character extraction and scene analysis have no shared dependencies
            log.info("Step 1/2: Extracting characters and analyzing scenes in parallel...");
            CompletableFuture<List<Character>> extractionFuture = CompletableFuture.supplyAsync(
                    () -> characterExtractionService.extractCharacters(chapter), taskExecutor);
            CompletableFuture<?> sceneFuture = CompletableFuture.supplyAsync(
                    () -> sceneAnalysisService.analyzeScenes(chapter), taskExecutor);

            List<Character> characters;
            try {
                characters = extractionFuture.get();
                log.info("Step 1/2: Found {} characters", characters.size());
                var scenes = sceneFuture.get();
                log.info("Step 2/2: Scene analysis complete");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof AiServiceException aise) throw aise;
                throw new AiServiceException("primary", "analysis",
                        "Analysis step failed for chapter " + chapterId, cause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiServiceException("primary", "analysis",
                        "Analysis interrupted for chapter " + chapterId, e);
            }

            chapter.setAnalysisStatus(AnalysisStatus.ANALYZED);
            chapter.setUpdatedAt(LocalDateTime.now());
            chapterRepository.save(chapter);

            // Fire world state update async — non-fatal, never blocks the pipeline
            try {
                worldStateService.updateWorldState(chapter, characters);
            } catch (Exception e) {
                log.warn("World state update could not be scheduled for ch.{}: {}", chapterId, e.getMessage());
            }

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
