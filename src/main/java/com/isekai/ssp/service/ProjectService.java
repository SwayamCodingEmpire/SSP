package com.isekai.ssp.service;

import com.isekai.ssp.dto.ProjectRequest;
import com.isekai.ssp.dto.ProjectResponse;
import com.isekai.ssp.entities.Project;
import com.isekai.ssp.helpers.ProjectStatus;
import com.isekai.ssp.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("Project title is required");
        }
        if (request.sourceLanguage() == null || request.sourceLanguage().isBlank()) {
            throw new IllegalArgumentException("Source language is required");
        }
        if (request.targetLanguage() == null || request.targetLanguage().isBlank()) {
            throw new IllegalArgumentException("Target language is required");
        }

        Project project = new Project();
        project.setTitle(request.title().trim());
        project.setSourceLanguage(request.sourceLanguage().trim());
        project.setTargetLanguage(request.targetLanguage().trim());
        project.setDescription(request.description());
        project.setTranslationStyle(request.translationStyle());
        project.setStatus(ProjectStatus.DRAFT);
        project.setCreatedAt(LocalDateTime.now());

        return toResponse(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAll() {
        return projectRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request) {
        Project project = findOrThrow(id);

        if (request.title() != null && !request.title().isBlank()) {
            project.setTitle(request.title().trim());
        }
        if (request.sourceLanguage() != null && !request.sourceLanguage().isBlank()) {
            project.setSourceLanguage(request.sourceLanguage().trim());
        }
        if (request.targetLanguage() != null && !request.targetLanguage().isBlank()) {
            project.setTargetLanguage(request.targetLanguage().trim());
        }
        // description and translationStyle are nullable — null means "clear the field"
        project.setDescription(request.description());
        project.setTranslationStyle(request.translationStyle());
        project.setUpdatedAt(LocalDateTime.now());

        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public void delete(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new IllegalArgumentException("Project not found: " + id);
        }
        projectRepository.deleteById(id);
    }

    // -------------------------------------------------------------------------

    private Project findOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
    }

    private ProjectResponse toResponse(Project p) {
        return new ProjectResponse(
                p.getId(),
                p.getTitle(),
                p.getSourceLanguage(),
                p.getTargetLanguage(),
                p.getStatus(),
                p.getDescription(),
                p.getOriginalFileName(),
                p.getFileFormat(),
                p.getTranslationStyle(),
                p.getChapters() != null ? p.getChapters().size() : 0,
                p.getCharacters() != null ? p.getCharacters().size() : 0,
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}