package com.isekai.ssp.service;

import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.Project;
import com.isekai.ssp.entities.TranslationMemory;
import com.isekai.ssp.repository.TranslationMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages Translation Memory (TM) entries.
 * Auto-extracts segments from accepted translations and stores them for reuse.
 * TM entries are embedded in pgvector for semantic retrieval during translation.
 */
@Service
public class TranslationMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(TranslationMemoryService.class);
    private static final int SEGMENT_MIN_LENGTH = 20;
    private static final int SEGMENT_MAX_LENGTH = 500;

    private final TranslationMemoryRepository tmRepository;
    private final NarrativeEmbeddingService embeddingService;

    public TranslationMemoryService(
            TranslationMemoryRepository tmRepository,
            NarrativeEmbeddingService embeddingService) {
        this.tmRepository = tmRepository;
        this.embeddingService = embeddingService;
    }

    /**
     * Extract and store TM segments from an accepted translation.
     * Called after user accepts or edits a translation.
     */
    public List<TranslationMemory> extractAndStore(Chapter chapter) {
        Project project = chapter.getProject();
        String sourceText = chapter.getOriginalText();
        String targetText = chapter.getUserEditedText() != null
                ? chapter.getUserEditedText()
                : chapter.getTranslatedText();

        if (sourceText == null || targetText == null) return List.of();

        List<String> sourceSegments = splitIntoSentences(sourceText);
        List<String> targetSegments = splitIntoSentences(targetText);

        // Simple 1:1 alignment (best-effort for sentence-level TM)
        int count = Math.min(sourceSegments.size(), targetSegments.size());
        List<TranslationMemory> entries = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String src = sourceSegments.get(i).trim();
            String tgt = targetSegments.get(i).trim();

            if (src.length() < SEGMENT_MIN_LENGTH || tgt.length() < SEGMENT_MIN_LENGTH) continue;
            if (src.length() > SEGMENT_MAX_LENGTH) continue;

            TranslationMemory tm = new TranslationMemory();
            tm.setProject(project);
            tm.setSourceLanguage(project.getSourceLanguage());
            tm.setTargetLanguage(project.getTargetLanguage());
            tm.setSourceSegment(src);
            tm.setTargetSegment(tgt);
            tm.setContentType(project.getContentType());
            tm.setHumanVerified(chapter.getUserAccepted() != null && chapter.getUserAccepted());
            tm.setSourceChapter(chapter);
            tm.setCreatedAt(LocalDateTime.now());

            TranslationMemory saved = tmRepository.save(tm);
            entries.add(saved);
            embedTmEntry(saved);
        }

        logger.info("Extracted {} TM segments from chapter {}", entries.size(), chapter.getId());
        return entries;
    }

    /**
     * Import TM entries manually (e.g., from external TMX files).
     */
    public TranslationMemory importEntry(Project project, String sourceSegment,
                                          String targetSegment, String domainTag) {
        TranslationMemory tm = new TranslationMemory();
        tm.setProject(project);
        tm.setSourceLanguage(project.getSourceLanguage());
        tm.setTargetLanguage(project.getTargetLanguage());
        tm.setSourceSegment(sourceSegment);
        tm.setTargetSegment(targetSegment);
        tm.setContentType(project.getContentType());
        tm.setDomainTag(domainTag);
        tm.setHumanVerified(true);
        tm.setCreatedAt(LocalDateTime.now());

        TranslationMemory saved = tmRepository.save(tm);
        embedTmEntry(saved);
        return saved;
    }

    public List<TranslationMemory> findByProject(Long projectId) {
        return tmRepository.findByProjectId(projectId);
    }

    /**
     * Search TM by semantic similarity via pgvector.
     */
    public List<Document> searchSimilar(String text, Long projectId, int topK) {
        return embeddingService.findRelevantDocuments(text, projectId, "translation_memory", topK);
    }

    private void embedTmEntry(TranslationMemory tm) {
        embeddingService.embedTranslationMemory(tm);
    }

    private List<String> splitIntoSentences(String text) {
        // Simple sentence splitting — split on period/exclamation/question followed by space
        List<String> sentences = new ArrayList<>();
        String[] parts = text.split("(?<=[.!?])\\s+");
        for (String part : parts) {
            if (!part.isBlank()) {
                sentences.add(part.trim());
            }
        }
        return sentences;
    }
}
