package com.isekai.ssp.controller;

import com.isekai.ssp.dto.CharacterRelationshipResponse;
import com.isekai.ssp.dto.CharacterResponse;import com.isekai.ssp.dto.CharacterStateResponse;
import com.isekai.ssp.dto.ChapterProcessingResponse;
import com.isekai.ssp.dto.RelationshipStateResponse;
import com.isekai.ssp.dto.SceneResponse;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.Character;
import com.isekai.ssp.entities.CharacterRelationship;
import com.isekai.ssp.entities.CharacterState;
import com.isekai.ssp.entities.RelationshipState;
import com.isekai.ssp.entities.Scene;
import com.isekai.ssp.repository.CharacterRelationshipRepository;
import com.isekai.ssp.repository.CharacterRepository;
import com.isekai.ssp.repository.CharacterStateRepository;
import com.isekai.ssp.repository.ChapterRepository;
import com.isekai.ssp.repository.ProjectRepository;
import com.isekai.ssp.repository.RelationshipStateRepository;
import com.isekai.ssp.repository.SceneRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only narrative data endpoints, scoped to a project.
 *
 * GET /api/projects/{projectId}/characters      — all characters in the project
 * GET /api/projects/{projectId}/relationships   — all character relationships in the project
 * GET /api/projects/{projectId}/scenes          — all scenes in the project
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
@Transactional(readOnly = true)
public class NarrativeController {

    private final ProjectRepository projectRepository;
    private final ChapterRepository chapterRepository;
    private final CharacterRepository characterRepository;
    private final CharacterStateRepository characterStateRepository;
    private final CharacterRelationshipRepository relationshipRepository;
    private final RelationshipStateRepository relationshipStateRepository;
    private final SceneRepository sceneRepository;

    public NarrativeController(
            ProjectRepository projectRepository,
            ChapterRepository chapterRepository,
            CharacterRepository characterRepository,
            CharacterStateRepository characterStateRepository,
            CharacterRelationshipRepository relationshipRepository,
            RelationshipStateRepository relationshipStateRepository,
            SceneRepository sceneRepository) {
        this.projectRepository = projectRepository;
        this.chapterRepository = chapterRepository;
        this.characterRepository = characterRepository;
        this.characterStateRepository = characterStateRepository;
        this.relationshipRepository = relationshipRepository;
        this.relationshipStateRepository = relationshipStateRepository;
        this.sceneRepository = sceneRepository;
    }

    // -------------------------------------------------------------------------
    // Chapters
    // -------------------------------------------------------------------------

    @GetMapping(value = "/chapters", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ChapterProcessingResponse>> getChapters(@PathVariable Long projectId) {
        assertProjectExists(projectId);
        List<ChapterProcessingResponse> body = chapterRepository
                .findByProjectIdOrderByChapterNumber(projectId)
                .stream()
                .map(this::toChapterResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    // -------------------------------------------------------------------------
    // Characters
    // -------------------------------------------------------------------------

    @GetMapping(value = "/characters", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<CharacterResponse>> getCharacters(@PathVariable Long projectId) {
        assertProjectExists(projectId);
        List<CharacterResponse> body = characterRepository.findByProjectId(projectId)
                .stream()
                .map(this::toCharacterResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    // -------------------------------------------------------------------------
    // Relationships
    // -------------------------------------------------------------------------

    @GetMapping(value = "/relationships", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<CharacterRelationshipResponse>> getRelationships(
            @PathVariable Long projectId) {
        assertProjectExists(projectId);
        List<CharacterRelationshipResponse> body = relationshipRepository.findByProjectId(projectId)
                .stream()
                .map(this::toRelationshipResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    // -------------------------------------------------------------------------
    // Scenes
    // -------------------------------------------------------------------------

    @GetMapping(value = "/scenes", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<SceneResponse>> getScenes(@PathVariable Long projectId) {
        assertProjectExists(projectId);
        List<SceneResponse> body = sceneRepository.findByProjectId(projectId)
                .stream()
                .map(this::toSceneResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    // -------------------------------------------------------------------------
    // Character state arc
    // -------------------------------------------------------------------------

    @GetMapping(value = "/characters/{characterId}/states", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<CharacterStateResponse>> getCharacterStates(
            @PathVariable Long projectId,
            @PathVariable Long characterId) {
        assertProjectExists(projectId);
        List<CharacterStateResponse> body = characterStateRepository
                .findByCharacterIdOrderByChapterNumberAsc(characterId)
                .stream()
                .map(this::toCharacterStateResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    // -------------------------------------------------------------------------
    // Relationship history arc
    // -------------------------------------------------------------------------

    @GetMapping(value = "/relationships/{relationshipId}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<RelationshipStateResponse>> getRelationshipHistory(
            @PathVariable Long projectId,
            @PathVariable Long relationshipId) {
        assertProjectExists(projectId);
        List<RelationshipStateResponse> body = relationshipStateRepository
                .findByRelationshipId(relationshipId)
                .stream()
                .sorted(java.util.Comparator.comparingInt(RelationshipState::getChapterNumber))
                .map(this::toRelationshipStateResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    // -------------------------------------------------------------------------
    // Mappers
    // -------------------------------------------------------------------------

    private CharacterResponse toCharacterResponse(Character c) {
        return new CharacterResponse(
                c.getId(),
                c.getProject().getId(),
                c.getName(),
                c.getTranslatedName(),
                c.getAliases(),
                c.getDescription(),
                c.getPersonalityTraits(),
                c.getRole(),
                c.getVoiceExample(),
                c.getFirstAppearanceChapter(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    private CharacterRelationshipResponse toRelationshipResponse(CharacterRelationship r) {
        return new CharacterRelationshipResponse(
                r.getId(),
                r.getCharacter1().getId(),
                r.getCharacter1().getName(),
                r.getCharacter1().getTranslatedName(),
                r.getCharacter2().getId(),
                r.getCharacter2().getName(),
                r.getCharacter2().getTranslatedName(),
                r.getType(),
                r.getDescription(),
                r.getAffinity(),
                r.getEstablishedAtChapter(),
                r.getCreatedAt()
        );
    }

    private SceneResponse toSceneResponse(Scene s) {
        List<Long> chapterIds = s.getChapters() != null
                ? s.getChapters().stream().map(ch -> ch.getId()).toList()
                : List.of();
        return new SceneResponse(
                s.getId(),
                s.getProject().getId(),
                s.getSummary(),
                s.getType(),
                s.getLocation(),
                s.getTensionLevel(),
                s.getPace(),
                s.getTone(),
                s.getNarrativeTimeType(),
                s.getFlashbackToChapter(),
                chapterIds,
                s.getCreatedAt()
        );
    }

    private ChapterProcessingResponse toChapterResponse(Chapter c) {
        String preview = c.getOriginalText() != null && c.getOriginalText().length() > 200
                ? c.getOriginalText().substring(0, 200)
                : c.getOriginalText();
        String translationStatus = c.getTranslationStatus() != null
                ? c.getTranslationStatus().name()
                : null;
        return new ChapterProcessingResponse(
                c.getId(),
                c.getChapterNumber(),
                c.getTitle(),
                c.getStatus(),
                translationStatus,
                c.getAnalysisStatus() != null ? c.getAnalysisStatus().name() : null,
                preview,
                null   // fullOriginalText omitted in list — fetch individual chapter for full text
        );
    }

    // -------------------------------------------------------------------------

    private CharacterStateResponse toCharacterStateResponse(CharacterState s) {
        return new CharacterStateResponse(
                s.getId(),
                s.getCharacter().getId(),
                s.getChapterNumber(),
                s.getEmotionalState(),
                s.getPhysicalState(),
                s.getCurrentGoal(),
                s.getArcStage(),
                s.getAffiliation(),
                s.getLoyalty(),
                s.getDialogueEmotionType(),
                s.getDialogueEmotionIntensity(),
                s.getDialogueSummary(),
                s.getCreatedAt()
        );
    }

    private RelationshipStateResponse toRelationshipStateResponse(RelationshipState rs) {
        return new RelationshipStateResponse(
                rs.getId(),
                rs.getRelationship().getId(),
                rs.getChapterNumber(),
                rs.getType(),
                rs.getDescription(),
                rs.getAffinity(),
                rs.getDynamicsNote(),
                rs.getCreatedAt()
        );
    }

    private void assertProjectExists(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
    }
}