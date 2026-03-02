package com.isekai.ssp.controller;

import com.isekai.ssp.dto.TranslationStatusResponse;
import com.isekai.ssp.repository.ChapterRepository;
import com.isekai.ssp.service.TranslationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            @RequestParam(required = false) String provider) {
        chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
        translationService.translateChapterAsync(chapterId, provider);
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
}
