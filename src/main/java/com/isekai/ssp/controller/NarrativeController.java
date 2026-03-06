package com.isekai.ssp.controller;

import com.isekai.ssp.dto.CharacterRelationshipResponse;
import com.isekai.ssp.dto.CharacterResponse;
import com.isekai.ssp.dto.CharacterStateResponse;
import com.isekai.ssp.dto.ChapterProcessingResponse;
import com.isekai.ssp.dto.PersonalityResponse;
import com.isekai.ssp.dto.RelationshipStateResponse;
import com.isekai.ssp.dto.SceneResponse;
import com.isekai.ssp.entities.*;
import com.isekai.ssp.entities.Character;
import com.isekai.ssp.repository.CharacterPersonalityRepository;
import com.isekai.ssp.repository.CharacterRelationshipRepository;
import com.isekai.ssp.repository.CharacterRepository;
import com.isekai.ssp.repository.CharacterStateRepository;
import com.isekai.ssp.repository.ChapterRepository;
import com.isekai.ssp.repository.ContentSectionRepository;
import com.isekai.ssp.repository.PoeticFormRepository;
import com.isekai.ssp.repository.ProjectRepository;
import com.isekai.ssp.repository.RelationshipStateRepository;
import com.isekai.ssp.repository.SceneRepository;
import com.isekai.ssp.repository.ScriptElementRepository;
import com.isekai.ssp.repository.ThematicElementRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    private final CharacterPersonalityRepository personalityRepository;
    private final CharacterStateRepository characterStateRepository;
    private final CharacterRelationshipRepository relationshipRepository;
    private final RelationshipStateRepository relationshipStateRepository;
    private final SceneRepository sceneRepository;
    private final ContentSectionRepository contentSectionRepository;
    private final PoeticFormRepository poeticFormRepository;
    private final ScriptElementRepository scriptElementRepository;
    private final ThematicElementRepository thematicElementRepository;

    public NarrativeController(
            ProjectRepository projectRepository,
            ChapterRepository chapterRepository,
            CharacterRepository characterRepository,
            CharacterPersonalityRepository personalityRepository,
            CharacterStateRepository characterStateRepository,
            CharacterRelationshipRepository relationshipRepository,
            RelationshipStateRepository relationshipStateRepository,
            SceneRepository sceneRepository,
            ContentSectionRepository contentSectionRepository,
            PoeticFormRepository poeticFormRepository,
            ScriptElementRepository scriptElementRepository,
            ThematicElementRepository thematicElementRepository) {
        this.projectRepository = projectRepository;
        this.chapterRepository = chapterRepository;
        this.characterRepository = characterRepository;
        this.personalityRepository = personalityRepository;
        this.characterStateRepository = characterStateRepository;
        this.relationshipRepository = relationshipRepository;
        this.relationshipStateRepository = relationshipStateRepository;
        this.sceneRepository = sceneRepository;
        this.contentSectionRepository = contentSectionRepository;
        this.poeticFormRepository = poeticFormRepository;
        this.scriptElementRepository = scriptElementRepository;
        this.thematicElementRepository = thematicElementRepository;
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
    // Content Sections (academic/non-fiction/periodical)
    // -------------------------------------------------------------------------

    @GetMapping(value = "/sections", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getSections(@PathVariable Long projectId) {
        assertProjectExists(projectId);
        List<Map<String, Object>> body = contentSectionRepository.findByProjectId(projectId).stream()
                .map(s -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("id", s.getId());
                    map.put("chapterId", s.getChapter().getId());
                    map.put("type", s.getType() != null ? s.getType().name() : null);
                    map.put("title", s.getTitle());
                    map.put("summary", s.getSummary());
                    map.put("sequenceNumber", s.getSequenceNumber());
                    map.put("keyConcepts", s.getKeyConceptsJson());
                    map.put("createdAt", s.getCreatedAt());
                    return map;
                })
                .toList();
        return ResponseEntity.ok(body);
    }

    // -------------------------------------------------------------------------
    // Poetic Forms (poetry/song lyrics)
    // -------------------------------------------------------------------------

    @GetMapping(value = "/poetic-forms", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getPoeticForms(@PathVariable Long projectId) {
        assertProjectExists(projectId);
        List<Map<String, Object>> body = poeticFormRepository.findByProjectId(projectId).stream()
                .map(f -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("id", f.getId());
                    map.put("chapterId", f.getChapter().getId());
                    map.put("form", f.getForm() != null ? f.getForm().name() : null);
                    map.put("meterPattern", f.getMeterPattern());
                    map.put("rhymeScheme", f.getRhymeScheme());
                    map.put("stanzaCount", f.getStanzaCount());
                    map.put("linesPerStanza", f.getLinesPerStanza());
                    map.put("soundDevices", f.getSoundDevicesJson());
                    map.put("notes", f.getNotes());
                    map.put("createdAt", f.getCreatedAt());
                    return map;
                })
                .toList();
        return ResponseEntity.ok(body);
    }

    // -------------------------------------------------------------------------
    // Script Elements (TV/movie scripts)
    // -------------------------------------------------------------------------

    @GetMapping(value = "/script-elements", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getScriptElements(@PathVariable Long projectId) {
        assertProjectExists(projectId);
        List<Map<String, Object>> body = scriptElementRepository.findByProjectId(projectId).stream()
                .map(e -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("id", e.getId());
                    map.put("chapterId", e.getChapter().getId());
                    map.put("type", e.getType() != null ? e.getType().name() : null);
                    map.put("content", e.getContent());
                    map.put("characterName", e.getCharacterName());
                    map.put("sequenceNumber", e.getSequenceNumber());
                    map.put("parenthetical", e.getParenthetical());
                    map.put("notes", e.getNotes());
                    map.put("createdAt", e.getCreatedAt());
                    return map;
                })
                .toList();
        return ResponseEntity.ok(body);
    }

    // -------------------------------------------------------------------------
    // Thematic Elements (poetry, essays, non-fiction)
    // -------------------------------------------------------------------------

    @GetMapping(value = "/themes", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getThemes(@PathVariable Long projectId) {
        assertProjectExists(projectId);
        List<Map<String, Object>> body = thematicElementRepository.findByProjectId(projectId).stream()
                .map(t -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("id", t.getId());
                    map.put("chapterId", t.getChapter() != null ? t.getChapter().getId() : null);
                    map.put("themeType", t.getThemeType());
                    map.put("name", t.getName());
                    map.put("description", t.getDescription());
                    map.put("occurrences", t.getOccurrencesJson());
                    map.put("createdAt", t.getCreatedAt());
                    return map;
                })
                .toList();
        return ResponseEntity.ok(body);
    }

    // -------------------------------------------------------------------------
    // Mappers
    // -------------------------------------------------------------------------

    private CharacterResponse toCharacterResponse(Character c) {
        List<PersonalityResponse> personalities = personalityRepository.findByCharacterId(c.getId())
                .stream()
                .map(p -> new PersonalityResponse(
                        p.getId(),
                        p.getName(),
                        p.getDescription(),
                        p.getPersonalityTraits(),
                        p.getVoiceExample(),
                        p.getTriggerCondition(),
                        p.isPrimary(),
                        p.getCreatedAt()
                ))
                .toList();
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
                personalities,
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
                s.getActivePersonalityName(),
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