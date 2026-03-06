package com.isekai.ssp.controller;

import com.isekai.ssp.dto.ChapterProcessingRequest;
import com.isekai.ssp.dto.ChapterProcessingResponse;
import com.isekai.ssp.helpers.ContentType;
import com.isekai.ssp.helpers.FileFormat;
import com.isekai.ssp.repository.ProjectRepository;
import com.isekai.ssp.service.ChapterProcessingService;
import com.isekai.ssp.service.DocumentTextExtractor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * REST Controller for chapter processing.
 * Supports file upload (PDF, EPUB, DOCX, TXT) and direct text input.
 */
@RestController
@RequestMapping("/api/chapters")
public class ChapterProcessingController {

    private final ChapterProcessingService chapterProcessingService;
    private final DocumentTextExtractor documentTextExtractor;
    private final ProjectRepository projectRepository;

    public ChapterProcessingController(
            ChapterProcessingService chapterProcessingService,
            DocumentTextExtractor documentTextExtractor,
            ProjectRepository projectRepository) {
        this.chapterProcessingService = chapterProcessingService;
        this.documentTextExtractor = documentTextExtractor;
        this.projectRepository = projectRepository;
    }

    /**
     * Process a chapter from an uploaded file (PDF, EPUB, DOCX, TXT).
     * Text is automatically extracted from the file using Apache Tika.
     *
     * POST /api/chapters/upload
     * Content-Type: multipart/form-data
     *
     * @param file          The chapter file (PDF, EPUB, DOCX, or TXT)
     * @param projectId     The project ID
     * @param chapterNumber The chapter number
     * @param title         Chapter title (optional)
     * @return Chapter processing response
     */
    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ChapterProcessingResponse> uploadChapter(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long projectId,
            @RequestParam Integer chapterNumber,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String contentType) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Detect format from file extension
        FileFormat format = detectFormat(file.getOriginalFilename());

        // Extract text from uploaded file
        String extractedText;
        try {
            extractedText = documentTextExtractor.extractText(file.getInputStream(), format);
        } catch (IOException e) {
            throw new DocumentTextExtractor.DocumentExtractionException(
                    "Failed to read uploaded file: " + e.getMessage(), e);
        }

        // Update project content type if specified
        updateProjectContentType(projectId, contentType);

        // Process the extracted text through the existing pipeline
        ChapterProcessingResponse response = chapterProcessingService.processChapter(
                projectId,
                chapterNumber,
                title != null ? title : "Chapter " + chapterNumber,
                extractedText
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Process a chapter text (JSON format).
     */
    @PostMapping(
            value = "/process",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ChapterProcessingResponse> processChapter(
            @RequestBody ChapterProcessingRequest request) {

        ChapterProcessingResponse response = chapterProcessingService.processChapter(
                request.projectId(),
                request.chapterNumber(),
                request.title(),
                request.chapterText()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Process a chapter text (plain text format).
     * For copy-pasting text directly from browser.
     */
    @PostMapping(
            value = "/process-text",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ChapterProcessingResponse> processChapterText(
            @RequestParam Long projectId,
            @RequestParam Integer chapterNumber,
            @RequestParam(required = false) String title,@RequestParam(required = false) String contentType,
            @RequestBody String chapterText) {

        updateProjectContentType(projectId, contentType);

        ChapterProcessingResponse response = chapterProcessingService.processChapter(
                projectId,
                chapterNumber,
                title != null ? title : "Chapter " + chapterNumber,
                chapterText
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get status of a processed chapter.
     */
    @GetMapping(
            value = "/{chapterId}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ChapterProcessingResponse> getChapterStatus(
            @PathVariable Long chapterId) {

        ChapterProcessingResponse response = chapterProcessingService.getChapterStatus(chapterId);
        return ResponseEntity.ok(response);
    }

    /**
     * Sets the project's content type if a valid contentType string is provided.
     */
    private void updateProjectContentType(Long projectId, String contentType) {
        if (contentType == null || contentType.isBlank()) return;
        try {
            ContentType ct = ContentType.valueOf(contentType);
            projectRepository.findById(projectId).ifPresent(project -> {
                project.setContentType(ct);
                projectRepository.save(project);
            });
        } catch (IllegalArgumentException e) {
            // ignore invalid content type — use project default
        }
    }

    /**
     * Detects FileFormat from the filename extension.
     */
    private FileFormat detectFormat(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new DocumentTextExtractor.DocumentExtractionException(
                    "Cannot determine file format: no file extension found");
        }

        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();

        return switch (extension) {
            case "pdf" -> FileFormat.PDF;
            case "epub" -> FileFormat.EPUB;
            case "docx" -> FileFormat.DOCX;
            case "txt", "text" -> FileFormat.TXT;
            default -> throw new DocumentTextExtractor.DocumentExtractionException(
                    "Unsupported file format: ." + extension +
                    ". Supported formats: PDF, EPUB, DOCX, TXT");
        };
    }
}