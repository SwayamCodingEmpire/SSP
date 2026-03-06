package com.isekai.ssp.controller;

import com.isekai.ssp.dto.QualityReport;
import com.isekai.ssp.repository.ChapterRepository;
import com.isekai.ssp.service.QualityEstimationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/translation")
public class QualityController {

    private final QualityEstimationService qualityEstimationService;
    private final ChapterRepository chapterRepository;

    public QualityController(
            QualityEstimationService qualityEstimationService,
            ChapterRepository chapterRepository) {
        this.qualityEstimationService = qualityEstimationService;
        this.chapterRepository = chapterRepository;
    }

    @GetMapping(value = "/chapters/{chapterId}/quality", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<QualityReport> getQualityScore(@PathVariable Long chapterId) {
        chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

        return qualityEstimationService.getQualityReport(chapterId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/chapters/{chapterId}/quality")
    public ResponseEntity<Void> triggerQualityAssessment(@PathVariable Long chapterId) {
        chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

        qualityEstimationService.assessChapterQuality(chapterId);
        return ResponseEntity.accepted().build();
    }
}
