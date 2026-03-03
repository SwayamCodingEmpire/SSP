package com.isekai.ssp.service;

import com.isekai.ssp.entities.*;
import com.isekai.ssp.entities.Character;
import com.isekai.ssp.helpers.NarrativeTimeType;
import com.isekai.ssp.repository.CharacterRepository;
import com.isekai.ssp.repository.CharacterStateRepository;
import com.isekai.ssp.repository.GlossaryRepository;
import com.isekai.ssp.repository.SceneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Assembles context blocks for AI prompts.
 *
 * Translation context uses RAG (semantic retrieval from pgvector) rather than
 * dumping all characters/glossary for every prompt — which overflows context at scale.
 *
 * Character extraction and scene analysis contexts still use the full relational DB
 * (they need all known characters/scenes to avoid duplication).
 *
 * Flashback detection: when translating a FLASHBACK scene, character states are
 * retrieved from the target chapter number rather than the current one.
 */
@Service
public class ContextBuilderService {

    private static final Logger logger = LoggerFactory.getLogger(ContextBuilderService.class);

    // How many items to retrieve via RAG
    private static final int CHARACTER_TOP_K       = 6;
    private static final int GLOSSARY_TOP_K        = 10;
    private static final int STYLE_EXAMPLE_TOP_K   = 2;

    private final CharacterRepository characterRepository;
    private final GlossaryRepository glossaryRepository;
    private final SceneRepository sceneRepository;
    private final CharacterStateRepository characterStateRepository;
    private final NarrativeEmbeddingService embeddingService;

    public ContextBuilderService(
            CharacterRepository characterRepository,
            GlossaryRepository glossaryRepository,
            SceneRepository sceneRepository,
            CharacterStateRepository characterStateRepository,
            NarrativeEmbeddingService embeddingService) {
        this.characterRepository = characterRepository;
        this.glossaryRepository = glossaryRepository;
        this.sceneRepository = sceneRepository;
        this.characterStateRepository = characterStateRepository;
        this.embeddingService = embeddingService;
    }

    // -------------------------------------------------------------------------
    // Analysis context builders (use full relational data — not RAG)
    // -------------------------------------------------------------------------

    public String buildCharacterExtractionContext(Chapter chapter) {
        var sb = new StringBuilder();
        sb.append("## Chapter %d: %s\n\n".formatted(chapter.getChapterNumber(), chapter.getTitle()));

        List<Character> existing = characterRepository.findByProjectId(chapter.getProject().getId());
        if (!existing.isEmpty()) {
            sb.append("## Previously known characters in this project:\n");
            for (Character c : existing) {
                String aliasNote = (c.getAliases() != null && !c.getAliases().isEmpty())
                        ? " [also known as: " + String.join(", ", c.getAliases()) + "]"
                        : "";
                sb.append("- %s%s (%s): %s\n".formatted(c.getName(), aliasNote, c.getRole(), c.getDescription()));
            }
            sb.append("\n");
        }

        sb.append("## Chapter text:\n\n");
        sb.append(chapter.getOriginalText());
        return sb.toString();
    }

    public String buildSpeakerDetectionContext(Chapter chapter, List<Character> knownCharacters) {
        var sb = new StringBuilder();
        sb.append("## Known characters:\n");
        for (Character c : knownCharacters) {
            sb.append("- Name: %s | Role: %s | Traits: %s\n".formatted(
                    c.getName(),
                    c.getRole() != null ? c.getRole().name() : "UNKNOWN",
                    c.getPersonalityTraits() != null ? c.getPersonalityTraits() : "unknown"));
        }
        sb.append("\n## Chapter %d text:\n\n".formatted(chapter.getChapterNumber()));
        sb.append(chapter.getOriginalText());
        return sb.toString();
    }

    public String buildSceneAnalysisContext(Chapter chapter) {
        var sb = new StringBuilder();
        sb.append("## Chapter %d: %s\n\n".formatted(chapter.getChapterNumber(), chapter.getTitle()));
        sb.append(chapter.getOriginalText());
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Translation context builder (RAG-based)
    // -------------------------------------------------------------------------

    public TranslationContext buildTranslationContext(Chapter chapter, String textToTranslate) {
        Project project = chapter.getProject();
        Long projectId = project.getId();

        // Determine which chapter to use for character state retrieval
        // For flashback scenes, retrieve character states at the flashback target chapter
        List<Scene> scenes = sceneRepository.findByChapterId(chapter.getId());
        String sceneContext = formatSceneContext(scenes);
        String sceneTypeForRag = extractPrimarySceneType(scenes);
        int stateChapterNumber = resolveStateChapterNumber(chapter, scenes);

        // RAG: retrieve only semantically relevant context for this text chunk
        String characterBlock = buildRagCharacterBlock(textToTranslate, projectId, stateChapterNumber);
        String glossaryBlock  = buildRagGlossaryBlock(textToTranslate, projectId);
        String styleExamplesBlock = buildRagStyleExamplesBlock(textToTranslate, projectId, sceneTypeForRag);

        return new TranslationContext(
                project.getSourceLanguage(),
                project.getTargetLanguage(),
                glossaryBlock,
                characterBlock,
                sceneContext,
                project.getTranslationStyle() != null ? project.getTranslationStyle() : "",
                styleExamplesBlock,
                textToTranslate
        );
    }

    // -------------------------------------------------------------------------
    // RAG retrieval helpers
    // -------------------------------------------------------------------------

    private String buildRagCharacterBlock(String text, Long projectId, int stateChapterNumber) {
        try {
            List<Document> docs = embeddingService.findRelevantCharacters(text, projectId, CHARACTER_TOP_K);
            if (docs.isEmpty()) {
                // Fallback: if vector store is empty (before first embedding), use relational data
                List<Character> all = characterRepository.findByProjectId(projectId);
                return formatCharacters(all);
            }

            // For each character doc, try to enrich with their temporal state
            StringBuilder sb = new StringBuilder();
            for (Document doc : docs) {
                sb.append("- ").append(doc.getText()).append("\n");
                // Add temporal state info if available (key for flashback handling)
                String entityId = doc.getMetadata() != null
                        ? (String) doc.getMetadata().get("entity_id") : null;
                if (entityId != null) {
                    try {
                        Long charId = Long.parseLong(entityId);
                        characterStateRepository.findLatestStateAtOrBefore(charId, stateChapterNumber)
                                .ifPresent(state -> {
                                    if (state.getEmotionalState() != null || state.getCurrentGoal() != null) {
                                        sb.append("  [At ch.").append(stateChapterNumber).append("] ");
                                        if (state.getEmotionalState() != null)
                                            sb.append("State: ").append(state.getEmotionalState());
                                        if (state.getCurrentGoal() != null)
                                            sb.append(" | Goal: ").append(state.getCurrentGoal());
                                        if (state.getArcStage() != null)
                                            sb.append(" | Arc: ").append(state.getArcStage());
                                        sb.append("\n");
                                    }
                                });
                    } catch (NumberFormatException e) {
                        // ignore — metadata parse failure
                    }
                }
            }
            return sb.isEmpty() ? "(No relevant characters found)" : sb.toString().trim();
        } catch (Exception e) {
            logger.warn("RAG character retrieval failed, falling back to relational data: {}", e.getMessage());
            return formatCharacters(characterRepository.findByProjectId(projectId));
        }
    }

    private String buildRagGlossaryBlock(String text, Long projectId) {
        try {
            List<Document> docs = embeddingService.findRelevantGlossaryTerms(text, projectId, GLOSSARY_TOP_K);
            if (docs.isEmpty()) {
                // Fallback to enforced terms only
                List<Glossary> terms = glossaryRepository.findByProjectIdAndEnforceConsistencyTrue(projectId);
                return formatGlossary(terms);
            }
            return docs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n- ", "- ", ""));
        } catch (Exception e) {
            logger.warn("RAG glossary retrieval failed, falling back to relational data: {}", e.getMessage());
            return formatGlossary(glossaryRepository.findByProjectIdAndEnforceConsistencyTrue(projectId));
        }
    }

    private String buildRagStyleExamplesBlock(String text, Long projectId, String sceneType) {
        if (sceneType == null) return "";
        try {
            List<Document> docs = embeddingService.findStyleExamples(text, projectId, sceneType, STYLE_EXAMPLE_TOP_K);
            if (docs.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (Document doc : docs) {
                sb.append("Example ").append(i++).append(":\n");
                sb.append(doc.getText()).append("\n\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            logger.warn("RAG style examples retrieval failed: {}", e.getMessage());
            return "";
        }
    }

    // -------------------------------------------------------------------------
    // Flashback chapter resolution
    // -------------------------------------------------------------------------

    /**
     * Returns the chapter number to use for character state retrieval.
     * For FLASHBACK scenes, this is the flashback target chapter.
     * For PRESENT/FLASH_FORWARD, this is the current chapter.
     */
    private int resolveStateChapterNumber(Chapter chapter, List<Scene> scenes) {
        for (Scene scene : scenes) {
            if (scene.getNarrativeTimeType() == NarrativeTimeType.FLASHBACK
                    && scene.getFlashbackToChapter() != null) {
                logger.debug("Flashback detected: retrieving character states at chapter {}",
                        scene.getFlashbackToChapter());
                return scene.getFlashbackToChapter();
            }
        }
        return chapter.getChapterNumber();
    }

    private String extractPrimarySceneType(List<Scene> scenes) {
        return scenes.stream()
                .filter(s -> s.getType() != null)
                .findFirst()
                .map(s -> s.getType().name())
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // Formatters (for fallback relational data)
    // -------------------------------------------------------------------------

    private String formatSceneContext(List<Scene> scenes) {
        if (scenes.isEmpty()) return "";
        return scenes.stream()
                .map(s -> {
                    var sb = new StringBuilder();
                    if (s.getType() != null)             sb.append("Type: ").append(s.getType().name());
                    if (s.getTone() != null)             sb.append(" | Tone: ").append(s.getTone().name());
                    if (s.getPace() != null)             sb.append(" | Pace: ").append(s.getPace().name());
                    if (s.getTensionLevel() != null)     sb.append(" | Tension: ").append(s.getTensionLevel());
                    if (s.getNarrativeTimeType() != null && s.getNarrativeTimeType() != NarrativeTimeType.PRESENT)
                        sb.append(" | Time: ").append(s.getNarrativeTimeType().name());
                    if (s.getSummary() != null)          sb.append(" | ").append(s.getSummary());
                    return sb.toString();
                })
                .collect(Collectors.joining("\n"));
    }

    private String formatGlossary(List<Glossary> terms) {
        if (terms.isEmpty()) return "(No glossary terms defined yet)";
        return terms.stream()
                .map(g -> "- %s → %s (%s)".formatted(g.getOriginalTerm(), g.getTranslatedTerm(), g.getType()))
                .collect(Collectors.joining("\n"));
    }

    private String formatCharacters(List<Character> characters) {
        if (characters.isEmpty()) return "(No characters identified yet)";
        return characters.stream()
                .map(c -> "- %s (%s): %s | Traits: %s%s".formatted(
                        c.getName(),
                        c.getRole() != null ? c.getRole().name() : "UNKNOWN",
                        c.getDescription() != null ? c.getDescription() : "No description",
                        c.getPersonalityTraits() != null ? c.getPersonalityTraits() : "unknown",
                        c.getVoiceExample() != null ? " | Voice: \"" + c.getVoiceExample() + "\"" : ""))
                .collect(Collectors.joining("\n"));
    }

    // -------------------------------------------------------------------------
    // TranslationContext record
    // -------------------------------------------------------------------------

    public record TranslationContext(
            String sourceLanguage,
            String targetLanguage,
            String glossaryBlock,
            String characterBlock,
            String sceneContext,       // tone/pace/tension/type from SceneAnalysisService
            String styleGuide,         // project-level prose style descriptor
            String styleExamplesBlock, // RAG-retrieved few-shot examples for this scene type
            String textToTranslate
    ) {}
}