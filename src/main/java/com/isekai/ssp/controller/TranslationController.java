package com.isekai.ssp.controller;

import com.isekai.ssp.dto.SaveTranslationRequest;
import com.isekai.ssp.dto.TranslatedTextResponse;
import com.isekai.ssp.dto.TranslationStatusResponse;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.repository.ChapterRepository;
import org.springframework.transaction.annotation.Transactional;
import com.isekai.ssp.service.TranslationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/translation")
public class TranslationController {

    private final TranslationService translationService;
    private final ChapterRepository chapterRepository;

    public TranslationController(
            TranslationService translationService,
            ChapterRepository chapterRepository) {
        this.translationService = translationService;
        this.chapterRepository = chapterRepository;
    }

    @PostMapping("/chapters/{chapterId}")
    public ResponseEntity<Void> translateChapter(
            @PathVariable Long chapterId,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model) {
        chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
        // If model is specified, use it as the provider override (vLLM adapters are loaded by model name)
        String resolvedProvider = model != null ? model : provider;
        translationService.translateChapterAsync(chapterId, resolvedProvider);
        return ResponseEntity.accepted().build();
    }

    @GetMapping(value = "/chapters/{chapterId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TranslationStatusResponse> getTranslationStatus(@PathVariable Long chapterId) {
        var chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

        String status = switch (chapter.getStatus()) {
            case TRANSLATING -> "TRANSLATING";
            case COMPLETED -> "COMPLETED";
            default -> chapter.getTranslatedText() != null ? "PARTIAL" : "PENDING";
        };

        return ResponseEntity.ok(new TranslationStatusResponse(
                chapterId, status, translationService.getActiveProvider()));
    }

    /**
     * Saves the user's review of the AI-generated translation.
     * If accepted=true  → marks as accepted, userEditedText stays null (no duplicate storage).
     * If accepted=false → stores the user's modified text in userEditedText.
     */
    @PutMapping(
            value = "/chapters/{chapterId}/save",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<TranslatedTextResponse> saveReview(
            @PathVariable Long chapterId,
            @RequestBody SaveTranslationRequest request) {

        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

        if (chapter.getTranslatedText() == null) {
            return ResponseEntity.badRequest().build();
        }

        chapter.setUserAccepted(request.accepted());
        chapter.setReviewedAt(LocalDateTime.now());

        if (!request.accepted()) {
            if (request.editedText() == null || request.editedText().isBlank()) {
                throw new IllegalArgumentException("editedText is required when accepted=false");
            }
            chapter.setUserEditedText(request.editedText());
        } else {
            // accepted as-is — clear any previously saved edits
            chapter.setUserEditedText(null);
        }

        chapterRepository.save(chapter);

        return ResponseEntity.ok(new TranslatedTextResponse(
                chapter.getId(),
                chapter.getChapterNumber(),
                chapter.getTitle(),
                chapter.getTranslationStatus(),
                chapter.getTranslatedText(),
                chapter.getUserEditedText(),
                chapter.getUserAccepted(),
                chapter.getReviewedAt(),
                chapter.isChunked(),
                chapter.getTotalSegments(),
                chapter.getTranslatedSegments()
        ));
    }

    /**
     * Returns the full translated text for a chapter.
     * Returns 404 if the chapter hasn't been translated yet.
     */
    @GetMapping(value = "/chapters/{chapterId}/text", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TranslatedTextResponse> getTranslatedText(@PathVariable Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

        if (chapter.getTranslatedText() == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new TranslatedTextResponse(
                chapter.getId(),
                chapter.getChapterNumber(),
                chapter.getTitle(),
                chapter.getTranslationStatus(),
                chapter.getTranslatedText(),
                chapter.getUserEditedText(),
                chapter.getUserAccepted(),
                chapter.getReviewedAt(),
                chapter.isChunked(),
                chapter.getTotalSegments(),
                chapter.getTranslatedSegments()
        ));
    }
}
