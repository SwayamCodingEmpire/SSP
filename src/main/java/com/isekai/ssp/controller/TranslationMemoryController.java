package com.isekai.ssp.controller;

import com.isekai.ssp.entities.Project;
import com.isekai.ssp.entities.TranslationMemory;
import com.isekai.ssp.repository.ProjectRepository;
import com.isekai.ssp.service.TranslationMemoryService;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/translation-memory")
public class TranslationMemoryController {

    private final TranslationMemoryService tmService;
    private final ProjectRepository projectRepository;

    public TranslationMemoryController(
            TranslationMemoryService tmService,
            ProjectRepository projectRepository) {
        this.tmService = tmService;
        this.projectRepository = projectRepository;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TranslationMemoryResponse>> getTranslationMemory(
            @PathVariable Long projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        List<TranslationMemoryResponse> entries = tmService.findByProject(projectId).stream()
                .map(tm -> new TranslationMemoryResponse(
                        tm.getId(),
                        tm.getSourceSegment(),
                        tm.getTargetSegment(),
                        tm.getContentType() != null ? tm.getContentType().name() : null,
                        tm.getDomainTag(),
                        tm.getQualityScore(),
                        tm.getHumanVerified(),
                        tm.getCreatedAt().toString()
                ))
                .toList();
        return ResponseEntity.ok(entries);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TranslationMemoryResponse> importEntry(
            @PathVariable Long projectId,
            @RequestBody ImportTmRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        TranslationMemory tm = tmService.importEntry(
                project, request.sourceSegment(), request.targetSegment(), request.domainTag());

        return ResponseEntity.ok(new TranslationMemoryResponse(
                tm.getId(),
                tm.getSourceSegment(),
                tm.getTargetSegment(),
                tm.getContentType() != null ? tm.getContentType().name() : null,
                tm.getDomainTag(),
                tm.getQualityScore(),
                tm.getHumanVerified(),
                tm.getCreatedAt().toString()
        ));
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, String>>> searchTranslationMemory(
            @PathVariable Long projectId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        List<Document> results = tmService.searchSimilar(query, projectId, topK);
        List<Map<String, String>> response = results.stream()
                .map(doc -> Map.of("text", doc.getText(), "id", doc.getId()))
                .toList();
        return ResponseEntity.ok(response);
    }

    record TranslationMemoryResponse(
            Long id,
            String sourceSegment,
            String targetSegment,
            String contentType,
            String domainTag,
            Float qualityScore,
            Boolean humanVerified,
            String createdAt
    ) {}

    record ImportTmRequest(
            String sourceSegment,
            String targetSegment,
            String domainTag
    ) {}
}
