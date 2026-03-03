package com.isekai.ssp.service;

import com.isekai.ssp.entities.*;
import com.isekai.ssp.entities.Character;
import com.isekai.ssp.repository.CharacterRepository;
import com.isekai.ssp.repository.CharacterStateRepository;
import com.isekai.ssp.repository.GlossaryRepository;
import com.isekai.ssp.repository.RelationshipStateRepository;
import com.isekai.ssp.repository.SceneRepository;
import com.isekai.ssp.repository.StyleExampleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Embeds all narrative objects (characters, scenes, glossary terms, style examples,
 * character states) into pgvector for semantic RAG retrieval during translation.
 *
 * Each document is stored with metadata for filtered retrieval:
 * - "type"       → object type (character, character_state, scene, glossary, style_example)
 * - "project_id" → project scope
 * - "entity_id"  → the relational DB id
 *
 * Upsert pattern: if an entity already has a vectorDocId, delete that doc first, then add new.
 */
@Service
public class NarrativeEmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(NarrativeEmbeddingService.class);

    private final VectorStore vectorStore;
    private final CharacterRepository characterRepository;
    private final CharacterStateRepository characterStateRepository;
    private final RelationshipStateRepository relationshipStateRepository;
    private final SceneRepository sceneRepository;
    private final GlossaryRepository glossaryRepository;
    private final StyleExampleRepository styleExampleRepository;

    public NarrativeEmbeddingService(
            VectorStore vectorStore,
            CharacterRepository characterRepository,
            CharacterStateRepository characterStateRepository,
            RelationshipStateRepository relationshipStateRepository,
            SceneRepository sceneRepository,
            GlossaryRepository glossaryRepository,
            StyleExampleRepository styleExampleRepository) {
        this.vectorStore = vectorStore;
        this.characterRepository = characterRepository;
        this.characterStateRepository = characterStateRepository;
        this.relationshipStateRepository = relationshipStateRepository;
        this.sceneRepository = sceneRepository;
        this.glossaryRepository = glossaryRepository;
        this.styleExampleRepository = styleExampleRepository;
    }

    // -------------------------------------------------------------------------
    // Public embed methods — each called after save in the respective service
    // -------------------------------------------------------------------------

    @Async("aiTaskExecutor")
    public void embedCharacter(Character character) {
        String text = buildCharacterText(character);
        String docId = upsert(character.getVectorDocId(), text, Map.of(
                "type", "character",
                "project_id", character.getProject().getId().toString(),
                "entity_id", character.getId().toString(),
                "name", character.getName()
        ));
        character.setVectorDocId(docId);
        characterRepository.save(character);
        logger.debug("Embedded character {} (docId={})", character.getName(), docId);
    }

    @Async("aiTaskExecutor")
    public void embedCharacterState(CharacterState state) {
        String text = buildCharacterStateText(state);
        // Store chapter_number as Integer for correct numeric filtering (not String)
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("type", "character_state");
        metadata.put("project_id", state.getCharacter().getProject().getId().toString());
        metadata.put("entity_id", state.getId().toString());
        metadata.put("character_id", state.getCharacter().getId().toString());
        metadata.put("chapter_number", state.getChapterNumber());
        String docId = upsert(state.getVectorDocId(), text, metadata);
        state.setVectorDocId(docId);
        characterStateRepository.save(state);
        logger.debug("Embedded character state for {} at chapter {} (docId={})",
                state.getCharacter().getName(), state.getChapterNumber(), docId);
    }

    @Async("aiTaskExecutor")
    public void embedScene(Scene scene) {
        String text = buildSceneText(scene);
        String docId = upsert(scene.getVectorDocId(), text, Map.of(
                "type", "scene",
                "project_id", scene.getProject().getId().toString(),
                "entity_id", scene.getId().toString(),
                "scene_type", scene.getType() != null ? scene.getType().name() : "UNKNOWN"
        ));
        scene.setVectorDocId(docId);
        sceneRepository.save(scene);
        logger.debug("Embedded scene {} (docId={})", scene.getId(), docId);
    }

    @Async("aiTaskExecutor")
    public void embedGlossaryTerm(Glossary glossary) {
        String text = buildGlossaryText(glossary);
        String docId = upsert(glossary.getVectorDocId(), text, Map.of(
                "type", "glossary",
                "project_id", glossary.getProject().getId().toString(),
                "entity_id", glossary.getId().toString()
        ));
        glossary.setVectorDocId(docId);
        glossaryRepository.save(glossary);
        logger.debug("Embedded glossary term '{}' (docId={})", glossary.getOriginalTerm(), docId);
    }

    @Async("aiTaskExecutor")
    public void embedStyleExample(StyleExample example) {
        String text = buildStyleExampleText(example);
        String docId = upsert(example.getVectorDocId(), text, Map.of(
                "type", "style_example",
                "project_id", example.getProject().getId().toString(),
                "entity_id", example.getId().toString(),
                "scene_type", example.getSceneType().name()
        ));
        example.setVectorDocId(docId);
        styleExampleRepository.save(example);
        logger.debug("Embedded style example {} (docId={})", example.getId(), docId);
    }

    @Async("aiTaskExecutor")
    public void embedRelationshipState(RelationshipState state) {
        String text = buildRelationshipStateText(state);
        CharacterRelationship rel = state.getRelationship();
        // Store chapter_number as Integer for correct numeric filtering
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("type", "relationship_state");
        metadata.put("project_id", rel.getCharacter1().getProject().getId().toString());
        metadata.put("relationship_id", rel.getId().toString());
        metadata.put("chapter_number", state.getChapterNumber());
        String docId = upsert(state.getVectorDocId(), text, metadata);
        state.setVectorDocId(docId);
        relationshipStateRepository.save(state);
        logger.debug("Embedded relationship state for {}<->{} at chapter {} (docId={})",
                rel.getCharacter1().getName(), rel.getCharacter2().getName(),
                state.getChapterNumber(), docId);
    }

    // -------------------------------------------------------------------------
    // RAG search methods
    // -------------------------------------------------------------------------

    /**
     * Retrieve the most semantically relevant characters for a given text chunk.
     * Filters by project scope.
     */
    public List<Document> findRelevantCharacters(String text, Long projectId, int topK) {
        var b = new FilterExpressionBuilder();
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(text)
                        .topK(topK)
                        .filterExpression(b.and(
                                b.eq("type", "character"),
                                b.eq("project_id", projectId.toString())
                        ).build())
                        .build()
        );
    }

    /**
     * Retrieve relevant glossary terms for a text chunk.
     */
    public List<Document> findRelevantGlossaryTerms(String text, Long projectId, int topK) {
        var b = new FilterExpressionBuilder();
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(text)
                        .topK(topK)
                        .filterExpression(b.and(
                                b.eq("type", "glossary"),
                                b.eq("project_id", projectId.toString())
                        ).build())
                        .build()
        );
    }

    /**
     * Retrieve style examples that match the scene type of the text being translated.
     * Note: FilterExpressionBuilder.and() takes exactly 2 Op args — nest for 3+ conditions.
     */
    public List<Document> findStyleExamples(String text, Long projectId, String sceneType, int topK) {
        var b = new FilterExpressionBuilder();
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(text)
                        .topK(topK)
                        .filterExpression(b.and(
                                b.and(b.eq("type", "style_example"),
                                      b.eq("project_id", projectId.toString())),
                                b.eq("scene_type", sceneType)
                        ).build())
                        .build()
        );
    }

    /**
     * Retrieve character state snapshots at or before a given chapter number.
     * Used for flashback context retrieval.
     * Note: FilterExpressionBuilder.and() takes exactly 2 Op args — nest for 3+ conditions.
     */
    public List<Document> findCharacterStatesAtChapter(String text, Long projectId,
                                                        int chapterNumber, int topK) {
        var b = new FilterExpressionBuilder();
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(text)
                        .topK(topK)
                        .filterExpression(b.and(
                                b.and(b.eq("type", "character_state"),
                                      b.eq("project_id", projectId.toString())),
                                b.lte("chapter_number", chapterNumber)
                        ).build())
                        .build()
        );
    }

    // -------------------------------------------------------------------------
    // Text builders — content that gets embedded
    // -------------------------------------------------------------------------

    private String buildCharacterText(Character c) {
        var sb = new StringBuilder();
        sb.append("Character: ").append(c.getName());
        if (c.getAliases() != null && !c.getAliases().isEmpty())
            sb.append(" (also known as: ").append(String.join(", ", c.getAliases())).append(")");
        if (c.getRole() != null) sb.append(" | Role: ").append(c.getRole().name());
        if (c.getDescription() != null) sb.append(" | Description: ").append(c.getDescription());
        if (c.getPersonalityTraits() != null) sb.append(" | Traits: ").append(c.getPersonalityTraits());
        if (c.getVoiceExample() != null) sb.append(" | Voice: \"").append(c.getVoiceExample()).append("\"");
        return sb.toString();
    }

    private String buildCharacterStateText(CharacterState s) {
        var sb = new StringBuilder();
        sb.append("Character: ").append(s.getCharacter().getName());
        sb.append(" at chapter ").append(s.getChapterNumber());
        if (s.getEmotionalState() != null)          sb.append(" | Emotional: ").append(s.getEmotionalState());
        if (s.getPhysicalState() != null)           sb.append(" | Physical: ").append(s.getPhysicalState());
        if (s.getCurrentGoal() != null)             sb.append(" | Goal: ").append(s.getCurrentGoal());
        if (s.getArcStage() != null)                sb.append(" | Arc: ").append(s.getArcStage());
        if (s.getAffiliation() != null)             sb.append(" | Affiliation: ").append(s.getAffiliation());
        if (s.getLoyalty() != null)                 sb.append(" | Loyalty: ").append(s.getLoyalty());
        if (s.getNarrativeNotes() != null)          sb.append(" | Notes: ").append(s.getNarrativeNotes());
        if (s.getDialogueEmotionType() != null) {
            sb.append(" | Dialogue emotion: ").append(s.getDialogueEmotionType());
            if (s.getDialogueEmotionIntensity() != null)
                sb.append(" (").append(s.getDialogueEmotionIntensity()).append(")");
        }
        if (s.getDialogueSummary() != null)         sb.append(" | Voice: ").append(s.getDialogueSummary());
        return sb.toString();
    }

    private String buildRelationshipStateText(RelationshipState rs) {
        CharacterRelationship rel = rs.getRelationship();
        var sb = new StringBuilder();
        sb.append("Relationship: ").append(rel.getCharacter1().getName())
          .append(" <-> ").append(rel.getCharacter2().getName());
        sb.append(" at chapter ").append(rs.getChapterNumber());
        sb.append(" | Type: ").append(rs.getType().name());
        if (rs.getAffinity() != null)    sb.append(" | Affinity: ").append(rs.getAffinity());
        if (rs.getDescription() != null) sb.append(" | Description: ").append(rs.getDescription());
        if (rs.getDynamicsNote() != null) sb.append(" | Dynamics: ").append(rs.getDynamicsNote());
        return sb.toString();
    }

    private String buildSceneText(Scene s) {
        var sb = new StringBuilder();
        if (s.getType() != null)     sb.append("Scene type: ").append(s.getType().name());
        if (s.getTone() != null)     sb.append(" | Tone: ").append(s.getTone().name());
        if (s.getPace() != null)     sb.append(" | Pace: ").append(s.getPace().name());
        if (s.getLocation() != null) sb.append(" | Location: ").append(s.getLocation());
        if (s.getSummary() != null)  sb.append(" | Summary: ").append(s.getSummary());
        return sb.toString();
    }

    private String buildGlossaryText(Glossary g) {
        return "%s → %s (%s): %s".formatted(
                g.getOriginalTerm(),
                g.getTranslatedTerm(),
                g.getType() != null ? g.getType().name() : "TERM",
                g.getContextNotes() != null ? g.getContextNotes() : "");
    }

    private String buildStyleExampleText(StyleExample e) {
        return "Scene type: %s | Source: %s | Translation: %s%s".formatted(
                e.getSceneType().name(),
                e.getSourceText(),
                e.getTargetText(),
                e.getNotes() != null ? " | Notes: " + e.getNotes() : "");
    }

    // -------------------------------------------------------------------------
    // Upsert helper
    // -------------------------------------------------------------------------

    /**
     * Deletes the existing vector document (if any) and adds a fresh one.
     * Returns the new document ID.
     */
    private String upsert(String existingDocId, String text, Map<String, Object> metadata) {
        if (existingDocId != null) {
            try {
                vectorStore.delete(List.of(existingDocId));
            } catch (Exception e) {
                logger.warn("Failed to delete old vector doc {}: {}", existingDocId, e.getMessage());
            }
        }
        String newId = UUID.randomUUID().toString();
        Document doc = new Document(newId, text, metadata);
        vectorStore.add(List.of(doc));
        return newId;
    }
}