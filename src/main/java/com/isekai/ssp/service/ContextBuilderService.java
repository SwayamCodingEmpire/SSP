package com.isekai.ssp.service;

import com.isekai.ssp.domain.ContextType;
import com.isekai.ssp.domain.DomainContext;
import com.isekai.ssp.domain.DomainStrategy;
import com.isekai.ssp.entities.*;
import com.isekai.ssp.entities.Character;
import com.isekai.ssp.helpers.NarrativeTimeType;
import com.isekai.ssp.repository.CharacterPersonalityRepository;
import com.isekai.ssp.repository.CharacterRepository;
import com.isekai.ssp.repository.CharacterStateRepository;
import com.isekai.ssp.repository.GlossaryRepository;
import com.isekai.ssp.repository.SceneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Assembles context blocks for AI prompts.
 *
 * Translation context uses RAG (semantic retrieval from pgvector) rather than
 * dumping all characters/glossary for every prompt — which overflows context at scale.
 *
 * Now uses DomainStrategy to determine which context types to retrieve,
 * building a DomainContext record for strategy-based prompt construction.
 */
@Service
public class ContextBuilderService {

    private static final Logger logger = LoggerFactory.getLogger(ContextBuilderService.class);

    private static final int CHARACTER_TOP_K       = 6;
    private static final int GLOSSARY_TOP_K        = 10;
    private static final int STYLE_EXAMPLE_TOP_K   = 2;

    private final CharacterRepository characterRepository;
    private final CharacterPersonalityRepository personalityRepository;
    private final GlossaryRepository glossaryRepository;
    private final SceneRepository sceneRepository;
    private final CharacterStateRepository characterStateRepository;
    private final NarrativeEmbeddingService embeddingService;

    public ContextBuilderService(
            CharacterRepository characterRepository,
            CharacterPersonalityRepository personalityRepository,
            GlossaryRepository glossaryRepository,
            SceneRepository sceneRepository,
            CharacterStateRepository characterStateRepository,
            NarrativeEmbeddingService embeddingService) {
        this.characterRepository = characterRepository;
        this.personalityRepository = personalityRepository;
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
            sb.append("- Name: %s | Role: %s | Traits: %s".formatted(
                    c.getName(),
                    c.getRole() != null ? c.getRole().name() : "UNKNOWN",
                    c.getPersonalityTraits() != null ? c.getPersonalityTraits() : "unknown"));

            List<CharacterPersonality> personalities = personalityRepository.findByCharacterId(c.getId());
            if (personalities.size() > 1) {
                sb.append(" | Personalities: ");
                sb.append(personalities.stream()
                        .map(p -> p.getName()
                                + (p.isPrimary() ? " [primary]" : "")
                                + (p.getVoiceExample() != null ? ": \"" + p.getVoiceExample() + "\"" : ""))
                        .collect(java.util.stream.Collectors.joining("; ")));
            }
            sb.append("\n");
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
    // Domain-aware context builder (strategy-driven RAG)
    // -------------------------------------------------------------------------

    /**
     * Builds a DomainContext by retrieving only the context types required by the strategy.
     */
    public DomainContext buildDomainContext(Chapter chapter, String textToTranslate, DomainStrategy strategy) {
        Project project = chapter.getProject();
        Long projectId = project.getId();
        Set<ContextType> required = strategy.getRequiredContextTypes();

        // Scene/flashback resolution (needed for character state retrieval and scene context)
        List<Scene> scenes = sceneRepository.findByChapterId(chapter.getId());
        String sceneContext = required.contains(ContextType.SCENES) ? formatSceneContext(scenes) : "";
        String sceneTypeForRag = extractPrimarySceneType(scenes);
        int stateChapterNumber = resolveStateChapterNumber(chapter, scenes);

        // Retrieve only what the strategy needs
        String characterBlock = required.contains(ContextType.CHARACTERS)
                ? buildRagCharacterBlock(textToTranslate, projectId, stateChapterNumber)
                : "";
        String glossaryBlock = required.contains(ContextType.GLOSSARY)
                ? buildRagGlossaryBlock(textToTranslate, projectId)
                : "";
        String styleExamplesBlock = required.contains(ContextType.STYLE_EXAMPLES)
                ? buildRagStyleExamplesBlock(textToTranslate, projectId, sceneTypeForRag)
                : "";

        // New context types (return empty for now until services are fully wired)
        String terminologyBlock = required.contains(ContextType.TERMINOLOGY)
                ? buildTerminologyBlock(textToTranslate, projectId)
                : "";
        String themeBlock = required.contains(ContextType.THEMES)
                ? buildThemeBlock(projectId)
                : "";
        String translationMemoryBlock = required.contains(ContextType.TRANSLATION_MEMORY)
                ? buildTranslationMemoryBlock(textToTranslate, projectId)
                : "";

        String styleGuide = project.getStyleGuide() != null ? project.getStyleGuide()
                : (project.getTranslationStyle() != null ? project.getTranslationStyle() : "");

        return new DomainContext(
                project.getContentType(),
                project.getSourceLanguage(),
                project.getTargetLanguage(),
                glossaryBlock,
                characterBlock,
                sceneContext,
                styleGuide,
                styleExamplesBlock,
                terminologyBlock,
                themeBlock,
                translationMemoryBlock,
                textToTranslate
        );
    }

    /**
     * Legacy method — builds a TranslationContext for backward compatibility.
     * Used if any code still references the old record type.
     */
    public TranslationContext buildTranslationContext(Chapter chapter, String textToTranslate) {
        Project project = chapter.getProject();
        Long projectId = project.getId();

        List<Scene> scenes = sceneRepository.findByChapterId(chapter.getId());
        String sceneContext = formatSceneContext(scenes);
        String sceneTypeForRag = extractPrimarySceneType(scenes);
        int stateChapterNumber = resolveStateChapterNumber(chapter, scenes);

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
    // New context block builders (placeholder — will be enriched as services mature)
    // -------------------------------------------------------------------------

    private String buildTerminologyBlock(String text, Long projectId) {
        // Will be populated by TerminologyExtractionService results via RAG
        try {
            List<Document> docs = embeddingService.findRelevantDocuments(text, projectId, "terminology", 10);
            if (docs.isEmpty()) return "(No domain terminology extracted yet)";
            return docs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n- ", "- ", ""));
        } catch (Exception e) {
            logger.warn("Terminology retrieval failed: {}", e.getMessage());
            return "(No domain terminology extracted yet)";
        }
    }

    private String buildThemeBlock(Long projectId) {
        // Will be populated by ThemeExtractionService results via RAG
        try {
            List<Document> docs = embeddingService.findDocumentsByType(projectId, "theme", 5);
            if (docs.isEmpty()) return "";
            return docs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n- ", "## Themes\n- ", ""));
        } catch (Exception e) {
            logger.warn("Theme retrieval failed: {}", e.getMessage());
            return "";
        }
    }

    private String buildTranslationMemoryBlock(String text, Long projectId) {
        // Will be populated by TranslationMemoryService via RAG
        try {
            List<Document> docs = embeddingService.findRelevantDocuments(text, projectId, "translation_memory", 3);
            if (docs.isEmpty()) return "";
            StringBuilder sb = new StringBuilder("## Translation memory matches\n");
            int i = 1;
            for (Document doc : docs) {
                sb.append("Match ").append(i++).append(":\n");
                sb.append(doc.getText()).append("\n\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            logger.warn("Translation memory retrieval failed: {}", e.getMessage());
            return "";
        }
    }

    // -------------------------------------------------------------------------
    // RAG retrieval helpers
    // -------------------------------------------------------------------------

    private String buildRagCharacterBlock(String text, Long projectId, int stateChapterNumber) {
        try {
            List<Document> docs = embeddingService.findRelevantCharacters(text, projectId, CHARACTER_TOP_K);
            if (docs.isEmpty()) {
                List<Character> all = characterRepository.findByProjectId(projectId);
                return formatCharacters(all);
            }

            StringBuilder sb = new StringBuilder();
            for (Document doc : docs) {
                sb.append("- ").append(doc.getText()).append("\n");
                String entityId = doc.getMetadata() != null
                        ? (String) doc.getMetadata().get("entity_id") : null;
                if (entityId != null) {
                    try {
                        Long charId = Long.parseLong(entityId);
                        characterStateRepository.findLatestStateAtOrBefore(charId, stateChapterNumber)
                                .ifPresent(state -> {
                                    if (state.getEmotionalState() != null || state.getCurrentGoal() != null
                                            || state.getActivePersonalityName() != null) {
                                        sb.append("  [At ch.").append(stateChapterNumber).append("] ");
                                        if (state.getEmotionalState() != null)
                                            sb.append("State: ").append(state.getEmotionalState());
                                        if (state.getCurrentGoal() != null)
                                            sb.append(" | Goal: ").append(state.getCurrentGoal());
                                        if (state.getArcStage() != null)
                                            sb.append(" | Arc: ").append(state.getArcStage());
                                        if (state.getActivePersonalityName() != null) {
                                            sb.append(" | Active personality: ").append(state.getActivePersonalityName());
                                            personalityRepository.findByCharacterIdAndName(
                                                            charId, state.getActivePersonalityName())
                                                    .ifPresent(p -> {
                                                        if (p.getPersonalityTraits() != null)
                                                            sb.append(" | Traits: ").append(p.getPersonalityTraits());
                                                        if (p.getVoiceExample() != null)
                                                            sb.append(" | Voice: \"").append(p.getVoiceExample()).append("\"");
                                                    });
                                        }
                                        sb.append("\n");
                                    }
                                });
                    } catch (NumberFormatException e) {
                        // ignore
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
    // TranslationContext record (legacy, kept for backward compatibility)
    // -------------------------------------------------------------------------

    public record TranslationContext(
            String sourceLanguage,
            String targetLanguage,
            String glossaryBlock,
            String characterBlock,
            String sceneContext,
            String styleGuide,
            String styleExamplesBlock,
            String textToTranslate
    ) {}
}
