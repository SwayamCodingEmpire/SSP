package com.isekai.ssp.controller;

import com.isekai.ssp.dto.AnalysisStatusResponse;
import com.isekai.ssp.entities.Character;
import com.isekai.ssp.entities.Scene;
import com.isekai.ssp.repository.CharacterRepository;
import com.isekai.ssp.repository.ChapterRepository;
import com.isekai.ssp.repository.SceneRepository;
import com.isekai.ssp.service.ChapterAnalysisOrchestrator;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final ChapterAnalysisOrchestrator analysisOrchestrator;
    private final ChapterRepository chapterRepository;
    private final CharacterRepository characterRepository;
    private final SceneRepository sceneRepository;

    public AnalysisController(
            ChapterAnalysisOrchestrator analysisOrchestrator,
            ChapterRepository chapterRepository,
            CharacterRepository characterRepository,
            SceneRepository sceneRepository) {
        this.analysisOrchestrator = analysisOrchestrator;
        this.chapterRepository = chapterRepository;
        this.characterRepository = characterRepository;
        this.sceneRepository = sceneRepository;
    }

    @PostMapping("/chapters/{chapterId}")
    public ResponseEntity<Void> analyzeChapter(@PathVariable Long chapterId) {
        chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));
        analysisOrchestrator.analyzeChapterAsync(chapterId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping(value = "/chapters/{chapterId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnalysisStatusResponse> getAnalysisStatus(@PathVariable Long chapterId) {
        var chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterId));

        List<Character> characters = characterRepository.findByProjectId(chapter.getProject().getId());
        List<Scene> scenes = sceneRepository.findByChapterId(chapterId);

        String status;
        if (!characters.isEmpty() && !scenes.isEmpty()) {
            status = "ANALYZED";
        } else if (!characters.isEmpty() || !scenes.isEmpty()) {
            status = "ANALYZING";
        } else {
            status = "PENDING";
        }

        List<String> characterNames = characters.stream().map(Character::getName).toList();

        List<AnalysisStatusResponse.ScenePreview> scenePreviews = scenes.stream()
                .map(s -> new AnalysisStatusResponse.ScenePreview(
                        s.getType() != null ? s.getType().name() : null,
                        s.getSummary(),
                        s.getTensionLevel() != null ? s.getTensionLevel() : 0.0))
                .toList();

        return ResponseEntity.ok(new AnalysisStatusResponse(
                chapterId, status, characters.size(), scenes.size(),
                characterNames, scenePreviews));
    }
}
