package com.isekai.ssp.service;

import com.isekai.ssp.dto.ChapterProcessingResponse;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.Project;
import com.isekai.ssp.helpers.ChapterStatus;
import com.isekai.ssp.helpers.TranslationStatus;
import com.isekai.ssp.repository.ChapterRepository;
import com.isekai.ssp.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Main service for processing chapter text.
 * Stores full chapter text and optionally triggers AI analysis.
 */
@Service
public class ChapterProcessingService {

    private final ProjectRepository projectRepository;
    private final ChapterRepository chapterRepository;
    private final ChapterAnalysisOrchestrator chapterAnalysisOrchestrator;

    @Value("${ssp.ai.analysis.enabled:false}")
    private boolean analysisEnabled;

    public ChapterProcessingService(
            ProjectRepository projectRepository,
            ChapterRepository chapterRepository,
            ChapterAnalysisOrchestrator chapterAnalysisOrchestrator) {
        this.projectRepository = projectRepository;
        this.chapterRepository = chapterRepository;
        this.chapterAnalysisOrchestrator = chapterAnalysisOrchestrator;
    }

    @Transactional
    public ChapterProcessingResponse processChapter(
            Long projectId,
            Integer chapterNumber,
            String title,
            String chapterText) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        Chapter chapter = new Chapter();
        chapter.setProject(project);
        chapter.setChapterNumber(chapterNumber);
        chapter.setTitle(title != null ? title : "Chapter " + chapterNumber);
        chapter.setOriginalText(chapterText);
        chapter.setStatus(ChapterStatus.PARSED);
        chapter.setTranslationStatus(TranslationStatus.PENDING);
        chapter.setChunked(false);
        chapter.setCreatedAt(LocalDateTime.now());

        chapter = chapterRepository.save(chapter);

        if (analysisEnabled) {
            chapterAnalysisOrchestrator.analyzeChapterAsync(chapter.getId());
        }

        return buildResponse(chapter);
    }

    @Transactional(readOnly = true)
    public ChapterProcessingResponse getChapterStatus(Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
        return buildResponse(chapter);
    }

    private ChapterProcessingResponse buildResponse(Chapter chapter) {
        String preview = chapter.getOriginalText() != null
                ? chapter.getOriginalText().substring(0, Math.min(200, chapter.getOriginalText().length()))
                : null;

        return new ChapterProcessingResponse(
                chapter.getId(),
                chapter.getChapterNumber(),
                chapter.getTitle(),
                chapter.getStatus(),
                chapter.getTranslationStatus() != null ? chapter.getTranslationStatus().name() : null,
                preview
        );
    }
}
