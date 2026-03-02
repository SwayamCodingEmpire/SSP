package com.isekai.ssp.service;

import com.isekai.ssp.dto.CharacterExtractionResult;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.Character;
import com.isekai.ssp.entities.CharacterRelationship;
import com.isekai.ssp.entities.CharacterState;
import com.isekai.ssp.helpers.CharacterRole;
import com.isekai.ssp.helpers.RelationshipType;
import com.isekai.ssp.repository.CharacterRelationshipRepository;
import com.isekai.ssp.repository.CharacterRepository;
import com.isekai.ssp.repository.CharacterStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CharacterExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(CharacterExtractionService.class);

    private final ChatClient chatClient;
    private final CharacterRepository characterRepository;
    private final CharacterRelationshipRepository relationshipRepository;
    private final CharacterStateRepository characterStateRepository;
    private final ContextBuilderService contextBuilder;
    private final NarrativeEmbeddingService embeddingService;

    public CharacterExtractionService(
            ChatClient chatClient,
            CharacterRepository characterRepository,
            CharacterRelationshipRepository relationshipRepository,
            CharacterStateRepository characterStateRepository,
            ContextBuilderService contextBuilder,
            NarrativeEmbeddingService embeddingService) {
        this.chatClient = chatClient;
        this.characterRepository = characterRepository;
        this.relationshipRepository = relationshipRepository;
        this.characterStateRepository = characterStateRepository;
        this.contextBuilder = contextBuilder;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public List<Character> extractCharacters(Chapter chapter) {
        String context = contextBuilder.buildCharacterExtractionContext(chapter);

        BeanOutputConverter<CharacterExtractionResult> converter =
                new BeanOutputConverter<>(CharacterExtractionResult.class);

        try {
            String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(context + "\n\n" + converter.getFormat())
                    .call()
                    .content();

            CharacterExtractionResult result = converter.convert(response);

            // 1. Upsert characters and create per-chapter state snapshots
            List<Character> savedCharacters = new ArrayList<>();
            for (CharacterExtractionResult.ExtractedCharacter extracted : result.characters()) {
                Character character = upsertCharacter(chapter, extracted);
                savedCharacters.add(character);
                createCharacterState(chapter, character, extracted);
                embeddingService.embedCharacter(character);
            }

            // 2. Save relationships (this was previously missing — root cause of the bug)
            if (result.relationships() != null) {
                for (CharacterExtractionResult.ExtractedRelationship rel : result.relationships()) {
                    saveRelationship(chapter, rel, savedCharacters);
                }
            }

            return savedCharacters;

        } catch (Exception e) {
            throw new AiServiceException("primary", "character-extraction",
                    "Failed to extract characters for chapter " + chapter.getId(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Character upsert
    // -------------------------------------------------------------------------

    private Character upsertCharacter(Chapter chapter,
                                       CharacterExtractionResult.ExtractedCharacter extracted) {
        return characterRepository
                .findByProjectIdAndName(chapter.getProject().getId(), extracted.name())
                .map(existing -> {
                    // Update description/traits as the character's presentation can evolve
                    if (extracted.description() != null && !extracted.description().isBlank()) {
                        existing.setDescription(extracted.description());
                    }
                    if (extracted.personalityTraits() != null) {
                        existing.setPersonalityTraits(extracted.personalityTraits());
                    }
                    // Only update voiceExample if not already set (first good example wins)
                    if (existing.getVoiceExample() == null && extracted.voiceExample() != null) {
                        existing.setVoiceExample(extracted.voiceExample());
                    }
                    existing.setUpdatedAt(LocalDateTime.now());
                    return characterRepository.save(existing);
                })
                .orElseGet(() -> {
                    Character c = new Character();
                    c.setProject(chapter.getProject());
                    c.setName(extracted.name());
                    c.setDescription(extracted.description());
                    c.setPersonalityTraits(extracted.personalityTraits());
                    c.setRole(parseRole(extracted.role()));
                    c.setVoiceExample(extracted.voiceExample());
                    c.setFirstAppearanceChapter(chapter.getChapterNumber());
                    c.setCreatedAt(LocalDateTime.now());
                    return characterRepository.save(c);
                });
    }

    // -------------------------------------------------------------------------
    // Character state snapshot
    // -------------------------------------------------------------------------

    private void createCharacterState(Chapter chapter, Character character,
                                       CharacterExtractionResult.ExtractedCharacter extracted) {
        CharacterState state = new CharacterState();
        state.setCharacter(character);
        state.setChapter(chapter);
        state.setChapterNumber(chapter.getChapterNumber());
        state.setEmotionalState(extracted.emotionalState());
        state.setCurrentGoal(extracted.currentGoal());
        state.setArcStage(extracted.arcStage());
        state.setCreatedAt(LocalDateTime.now());
        CharacterState saved = characterStateRepository.save(state);
        embeddingService.embedCharacterState(saved);
    }

    // -------------------------------------------------------------------------
    // Relationship saving
    // -------------------------------------------------------------------------

    private void saveRelationship(Chapter chapter,
                                   CharacterExtractionResult.ExtractedRelationship rel,
                                   List<Character> chapterCharacters) {
        Optional<Character> c1 = resolveCharacter(rel.character1Name(), chapter, chapterCharacters);
        Optional<Character> c2 = resolveCharacter(rel.character2Name(), chapter, chapterCharacters);

        if (c1.isEmpty() || c2.isEmpty()) {
            logger.warn("Skipping relationship {}<->{}: one or both characters not found",
                    rel.character1Name(), rel.character2Name());
            return;
        }

        // Check for an existing relationship between these two characters
        List<CharacterRelationship> existing = relationshipRepository.findByCharacterId(c1.get().getId());
        boolean alreadyExists = existing.stream().anyMatch(r ->
                (r.getCharacter1().getId().equals(c1.get().getId()) && r.getCharacter2().getId().equals(c2.get().getId()))
                || (r.getCharacter1().getId().equals(c2.get().getId()) && r.getCharacter2().getId().equals(c1.get().getId())));

        if (alreadyExists) {
            logger.debug("Relationship between {} and {} already exists — skipping",
                    rel.character1Name(), rel.character2Name());
            return;
        }

        CharacterRelationship relationship = new CharacterRelationship();
        relationship.setCharacter1(c1.get());
        relationship.setCharacter2(c2.get());
        relationship.setType(parseRelationshipType(rel.type()));
        relationship.setDescription(rel.description());
        relationship.setAffinity(rel.affinity());
        relationship.setEstablishedAtChapter(chapter.getChapterNumber());
        relationship.setCreatedAt(LocalDateTime.now());
        relationshipRepository.save(relationship);
        logger.debug("Saved relationship: {} <-[{}]-> {}", rel.character1Name(), rel.type(), rel.character2Name());
    }

    private Optional<Character> resolveCharacter(String name, Chapter chapter,
                                                   List<Character> chapterCharacters) {
        // First try the characters already saved for this chapter
        Optional<Character> fromChapter = chapterCharacters.stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst();
        if (fromChapter.isPresent()) return fromChapter;
        // Fall back to the full project character list
        return characterRepository.findByProjectIdAndName(chapter.getProject().getId(), name);
    }

    // -------------------------------------------------------------------------
    // Parsers
    // -------------------------------------------------------------------------

    private CharacterRole parseRole(String role) {
        try { return CharacterRole.valueOf(role); }
        catch (Exception e) { return CharacterRole.SUPPORTING; }
    }

    private RelationshipType parseRelationshipType(String type) {
        try { return RelationshipType.valueOf(type); }
        catch (Exception e) { return RelationshipType.NEUTRAL; }
    }

    // -------------------------------------------------------------------------
    // System prompt
    // -------------------------------------------------------------------------

    private static final String SYSTEM_PROMPT = """
            You are a literary analyst specializing in character identification and relationship mapping.
            Given a chapter from a novel, identify ALL characters who appear or are mentioned.

            For each CHARACTER, determine:
            - name: The character's name as it appears in the text (original language)
            - description: Physical appearance and notable traits observed in this chapter
            - personalityTraits: Comma-separated personality traits evident from behavior/dialogue
            - role: One of PROTAGONIST, ANTAGONIST, SUPPORTING, MINOR
            - voiceExample: One short, representative line of dialogue that captures how they speak.
                           Choose a line that shows their register (formal/crude/lyrical/etc.).
                           Leave null if the character has no dialogue in this chapter.
            - emotionalState: Their emotional/mental state in this chapter, e.g. "grief-stricken, resolute"
            - currentGoal: Their primary motivation right now, e.g. "find his missing sister"
            - arcStage: Their narrative arc position, e.g. "reluctant hero", "betrayed mentor", "at lowest point"

            For each RELATIONSHIP between characters in this chapter, determine:
            - character1Name: Name of the first character
            - character2Name: Name of the second character
            - type: One of ALLY, ENEMY, FAMILY, ROMANTIC, NEUTRAL, MENTOR
            - description: Descriptive label, e.g. "Childhood friends", "Sworn enemies", "Mentor and student"
            - affinity: -1.0 (deeply hostile) to 1.0 (deeply bonded). Use 0.0 for NEUTRAL.

            Be thorough — include characters mentioned in dialogue even if they don't appear.
            Only report relationships where both characters appear or are mentioned in THIS chapter.

            Respond ONLY with valid JSON matching the requested format.
            """;
}