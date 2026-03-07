package com.isekai.ssp.service;

import com.isekai.ssp.entities.*;
import com.isekai.ssp.entities.Character;
import com.isekai.ssp.helpers.NarrativeTimeType;
import com.isekai.ssp.repository.CharacterRelationshipRepository;
import com.isekai.ssp.repository.CharacterRepository;
import com.isekai.ssp.repository.CharacterStateRepository;
import com.isekai.ssp.repository.GlossaryRepository;
import com.isekai.ssp.repository.SceneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Assembles context blocks for AI prompts.
 *
 * Translation context uses RAG (semantic retrieval from pgvector) rather than
 * dumping all characters/glossary for every prompt — which overflows context at scale.
 *
 * Character extraction context uses GraphRAG: Java-side name/alias scan of the chapter
 * text to find seed characters, then 1-hop relationship graph expansion, plus a fixed-size
 * recurrent world state block from WorldStateService. Token cost is O(scene size) not
 * O(total characters).
 *
 * Scene analysis context still uses chapter text only (no character context needed).
 *
 * Flashback detection: when translating a FLASHBACK scene, character states are
 * retrieved from the target chapter number rather than the current one.
 */
@Service
public class ContextBuilderService {

    private static final Logger logger = LoggerFactory.getLogger(ContextBuilderService.class);

    // How many items to retrieve via RAG (translation only)
    private static final int CHARACTER_TOP_K       = 6;
    private static final int GLOSSARY_TOP_K        = 10;
    private static final int STYLE_EXAMPLE_TOP_K   = 2;

    // Fallback top-K for GraphRAG vector search (when name scan finds nothing)
    private static final int GRAPH_RAG_VECTOR_FALLBACK_K = 10;

    private final CharacterRepository characterRepository;
    private final GlossaryRepository glossaryRepository;
    private final SceneRepository sceneRepository;
    private final CharacterStateRepository characterStateRepository;
    private final NarrativeEmbeddingService embeddingService;
    private final CharacterRelationshipRepository relationshipRepository;
    private final WorldStateService worldStateService;
    private final Executor taskExecutor;

    public ContextBuilderService(
            CharacterRepository characterRepository,
            GlossaryRepository glossaryRepository,
            SceneRepository sceneRepository,
            CharacterStateRepository characterStateRepository,
            NarrativeEmbeddingService embeddingService,
            CharacterRelationshipRepository relationshipRepository,
            WorldStateService worldStateService,
            @Qualifier("aiTaskExecutor") Executor taskExecutor) {
        this.characterRepository = characterRepository;
        this.glossaryRepository = glossaryRepository;
        this.sceneRepository = sceneRepository;
        this.characterStateRepository = characterStateRepository;
        this.embeddingService = embeddingService;
        this.relationshipRepository = relationshipRepository;
        this.worldStateService = worldStateService;
        this.taskExecutor = taskExecutor;
    }

    // -------------------------------------------------------------------------
    // Analysis context builders
    // -------------------------------------------------------------------------

    /**
     * Builds the extraction prompt context using GraphRAG + recurrent world state.
     *
     * Block 1 (fixed size): World state summary from the prior chapter (~350 words).
     *   Provides faction/tension continuity and deduplication safety for off-scene characters.
     *
     * Block 2 (bounded by scene scope): GraphRAG-retrieved characters.
     *   Name/alias scan of the chapter text finds seeds; 1-hop relationship graph expansion
     *   adds closely connected characters. Typically 6-15 characters regardless of total cast.
     *
     * Block 3: Full chapter text.
     *
     * Token cost: O(scene size) instead of O(total characters). Flat from chapter 50 onwards.
     */
    public String buildCharacterExtractionContext(Chapter chapter) {
        var sb = new StringBuilder();
        sb.append("## Chapter %d: %s\n\n".formatted(chapter.getChapterNumber(), chapter.getTitle()));

        // Block 1: World state — fixed size, generated from prior chapter's analysis
        worldStateService.getLatestSummary(chapter.getProject().getId()).ifPresent(summary -> {
            sb.append("## Current world state (as of last analyzed chapter):\n");
            sb.append(summary).append("\n\n");
        });

        // Block 2: GraphRAG — scene-relevant characters only
        List<Character> relevant = graphRagRetrieve(chapter);
        if (!relevant.isEmpty()) {
            sb.append("## Previously known characters relevant to this chapter:\n");
            for (Character c : relevant) {
                String aliasNote = (c.getAliases() != null && !c.getAliases().isEmpty())
                        ? " [also known as: " + String.join(", ", c.getAliases()) + "]"
                        : "";
                sb.append("- %s%s (%s): %s\n".formatted(
                        c.getName(), aliasNote,
                        c.getRole() != null ? c.getRole().name() : "UNKNOWN",
                        c.getDescription() != null ? c.getDescription() : ""));
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
    // GraphRAG retrieval
    // -------------------------------------------------------------------------

    /**
     * Retrieves characters relevant to this chapter via GraphRAG:
     *
     * 1. Load all project characters (DB only — never sent to AI).
     * 2. Scan the full chapter text for any name/alias matches (seed set).
     * 3. Batch-fetch all relationships for seed IDs; expand to 1-hop neighbors.
     * 4. Return only the relevant subset.
     *
     * Fallback: if the name scan finds nothing (CJK scripts, pronoun-only chapters,
     * or very early chapters with sparse text), falls back to vector similarity search.
     *
     * Returns empty list for chapter 1 (no prior characters exist yet — correct behavior).
     */
    private List<Character> graphRagRetrieve(Chapter chapter) {
        Long projectId = chapter.getProject().getId();
        String text = chapter.getOriginalText();

        // Load all characters for name scanning — DB query only, NEVER sent to AI
        List<Character> allCharacters = characterRepository.findByProjectId(projectId);
        if (allCharacters.isEmpty()) {
            return List.of(); // Chapter 1: no prior characters — AI identifies from scratch
        }

        // Name/alias scan in Java — find seed characters mentioned in this chapter
        Set<Long> seedIds = new HashSet<>();
        for (Character c : allCharacters) {
            if (textContainsName(text, c.getName())) {
                seedIds.add(c.getId());
                continue;
            }
            if (c.getAliases() != null) {
                for (String alias : c.getAliases()) {
                    if (textContainsName(text, alias)) {
                        seedIds.add(c.getId());
                        break;
                    }
                }
            }
        }

        // Fallback: vector similarity search if name scan found nothing
        if (seedIds.isEmpty()) {
            logger.debug("GraphRAG ch.{}: no name matches found — falling back to vector search",
                    chapter.getChapterNumber());
            try {
                List<Document> docs = embeddingService.findRelevantCharacters(
                        text, projectId, GRAPH_RAG_VECTOR_FALLBACK_K);
                Set<Long> vecIds = docs.stream()
                        .filter(d -> d.getMetadata() != null && d.getMetadata().get("entity_id") != null)
                        .map(d -> {
                            try { return Long.parseLong((String) d.getMetadata().get("entity_id")); }
                            catch (NumberFormatException e) { return null; }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                return allCharacters.stream().filter(c -> vecIds.contains(c.getId())).toList();
            } catch (Exception e) {
                logger.warn("GraphRAG vector fallback failed for ch.{}: {}",
                        chapter.getChapterNumber(), e.getMessage());
                return List.of();
            }
        }

        // 1-hop graph expansion — single batch query for all seed IDs
        List<CharacterRelationship> relationships = relationshipRepository.findByCharacterIds(seedIds);
        Set<Long> expandedIds = new HashSet<>(seedIds);
        for (CharacterRelationship r : relationships) {
            // getId() on Hibernate proxy does not trigger lazy loading
            expandedIds.add(r.getCharacter1().getId());
            expandedIds.add(r.getCharacter2().getId());
        }

        logger.debug("GraphRAG ch.{}: seeds={}, expanded={} (of {} total project characters)",
                chapter.getChapterNumber(), seedIds.size(), expandedIds.size(), allCharacters.size());

        return allCharacters.stream().filter(c -> expandedIds.contains(c.getId())).toList();
    }

    /**
     * Case-insensitive whole-word match. Uses \b word boundary to prevent
     * "Li" matching inside "Oliver". Pattern.quote handles special chars in names
     * (e.g. hyphens in "Jin-woo"). Falls back to contains() for CJK text where
     * \b boundaries don't apply.
     */
    private boolean textContainsName(String text, String name) {
        if (name == null || name.isBlank()) return false;
        try {
            return Pattern.compile("(?i)\\b" + Pattern.quote(name) + "\\b")
                    .matcher(text)
                    .find();
        } catch (Exception e) {
            return text.toLowerCase().contains(name.toLowerCase());
        }
    }

    // -------------------------------------------------------------------------
    // Translation context builder (RAG-based — unchanged)
    // -------------------------------------------------------------------------

    public TranslationContext buildTranslationContext(Chapter chapter, String textToTranslate) {
        Project project = chapter.getProject();
        Long projectId = project.getId();

        List<Scene> scenes = sceneRepository.findByChapterId(chapter.getId());
        String sceneContext = formatSceneContext(scenes);
        String sceneTypeForRag = extractPrimarySceneType(scenes);
        int stateChapterNumber = resolveStateChapterNumber(chapter, scenes);

        // Parallelize the 3 embedding calls — each makes 1 independent vector search
        final String textFinal = textToTranslate;
        final int stateChNum = stateChapterNumber;
        final String sceneTypeFinal = sceneTypeForRag;
        CompletableFuture<String> charFuture = CompletableFuture.supplyAsync(
                () -> buildRagCharacterBlock(textFinal, projectId, stateChNum), taskExecutor);
        CompletableFuture<String> glossFuture = CompletableFuture.supplyAsync(
                () -> buildRagGlossaryBlock(textFinal, projectId), taskExecutor);
        CompletableFuture<String> styleFuture = CompletableFuture.supplyAsync(
                () -> buildRagStyleExamplesBlock(textFinal, projectId, sceneTypeFinal), taskExecutor);

        String characterBlock, glossaryBlock, styleExamplesBlock;
        try {
            characterBlock     = charFuture.get();
            glossaryBlock      = glossFuture.get();
            styleExamplesBlock = styleFuture.get();
        } catch (ExecutionException e) {
            // Individual helpers already have try-catch fallbacks — secondary safety net only.
            logger.warn("Parallel RAG failed, falling back to sequential: {}", e.getMessage());
            characterBlock     = buildRagCharacterBlock(textToTranslate, projectId, stateChapterNumber);
            glossaryBlock      = buildRagGlossaryBlock(textToTranslate, projectId);
            styleExamplesBlock = buildRagStyleExamplesBlock(textToTranslate, projectId, sceneTypeForRag);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            characterBlock     = buildRagCharacterBlock(textToTranslate, projectId, stateChapterNumber);
            glossaryBlock      = buildRagGlossaryBlock(textToTranslate, projectId);
            styleExamplesBlock = buildRagStyleExamplesBlock(textToTranslate, projectId, sceneTypeForRag);
        }

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
    // RAG retrieval helpers (translation only)
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
    // TranslationContext record
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
