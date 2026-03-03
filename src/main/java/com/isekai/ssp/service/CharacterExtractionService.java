package com.isekai.ssp.service;

import com.isekai.ssp.dto.CharacterExtractionResult;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.Character;
import com.isekai.ssp.entities.CharacterRelationship;
import com.isekai.ssp.entities.CharacterState;
import com.isekai.ssp.entities.RelationshipState;
import com.isekai.ssp.helpers.CharacterRole;
import com.isekai.ssp.helpers.RelationshipType;
import com.isekai.ssp.llm.LlmProvider;
import com.isekai.ssp.llm.LlmProviderRegistry;
import com.isekai.ssp.repository.CharacterRelationshipRepository;
import com.isekai.ssp.repository.CharacterRepository;
import com.isekai.ssp.repository.CharacterStateRepository;
import com.isekai.ssp.repository.RelationshipStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final LlmProviderRegistry providerRegistry;
    private final CharacterRepository characterRepository;
    private final CharacterRelationshipRepository relationshipRepository;
    private final RelationshipStateRepository relationshipStateRepository;
    private final CharacterStateRepository characterStateRepository;
    private final ContextBuilderService contextBuilder;
    private final NarrativeEmbeddingService embeddingService;

    public CharacterExtractionService(
            LlmProviderRegistry providerRegistry,
            CharacterRepository characterRepository,
            CharacterRelationshipRepository relationshipRepository,
            RelationshipStateRepository relationshipStateRepository,
            CharacterStateRepository characterStateRepository,
            ContextBuilderService contextBuilder,
            NarrativeEmbeddingService embeddingService) {
        this.providerRegistry = providerRegistry;
        this.characterRepository = characterRepository;
        this.relationshipRepository = relationshipRepository;
        this.relationshipStateRepository = relationshipStateRepository;
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
            LlmProvider provider = providerRegistry.resolve(null);
            String response = provider.generate(
                    SYSTEM_PROMPT,
                    context + "\n\n" + converter.getFormat()
            );

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
        Optional<Character> found = characterRepository
                .findByProjectIdAndName(chapter.getProject().getId(), extracted.name());

        return found.map(existing -> {
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
                    // Only update translatedName if not already set
                    if (existing.getTranslatedName() == null && extracted.translatedName() != null) {
                        existing.setTranslatedName(extracted.translatedName());
                    }
                    // Merge any new aliases reported for this chapter
                    if (extracted.aliases() != null) {
                        extracted.aliases().forEach(alias -> mergeAlias(existing, alias));
                    }
                    existing.setUpdatedAt(LocalDateTime.now());
                    return characterRepository.save(existing);
                })
                .orElseGet(() -> {
                    Character c = new Character();
                    c.setProject(chapter.getProject());
                    c.setName(extracted.name());
                    c.setTranslatedName(extracted.translatedName());
                    c.setDescription(extracted.description());
                    c.setPersonalityTraits(extracted.personalityTraits());
                    c.setRole(parseRole(extracted.role()));
                    c.setVoiceExample(extracted.voiceExample());
                    c.setFirstAppearanceChapter(chapter.getChapterNumber());
                    if (extracted.aliases() != null) {
                        extracted.aliases().forEach(alias -> mergeAlias(c, alias));
                    }
                    c.setCreatedAt(LocalDateTime.now());
                    return characterRepository.save(c);
                });
    }

    /** Adds an alias to the character's list only if it is not already present. */
    private void mergeAlias(Character character, String alias) {
        if (alias == null || alias.isBlank()) return;
        if (character.getAliases() == null) {
            character.setAliases(new ArrayList<>());
        }
        boolean alreadyPresent = character.getAliases().stream()
                .anyMatch(a -> a.equalsIgnoreCase(alias));
        if (!alreadyPresent) {
            character.getAliases().add(alias);
        }
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
        state.setAffiliation(extracted.affiliation());
        state.setLoyalty(extracted.loyalty());
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

        RelationshipType type = parseRelationshipType(rel.type());
        Optional<CharacterRelationship> existingOpt =
                relationshipRepository.findByCharacterPair(c1.get().getId(), c2.get().getId());

        CharacterRelationship relationship;
        if (existingOpt.isPresent()) {
            // Update the flat record to reflect the latest state
            relationship = existingOpt.get();
            relationship.setType(type);
            relationship.setDescription(rel.description());
            relationship.setAffinity(rel.affinity());
            relationship.setUpdatedAt(LocalDateTime.now());
            relationship = relationshipRepository.save(relationship);
            logger.debug("Updated relationship: {} <-[{}]-> {} (affinity={})",
                    rel.character1Name(), rel.type(), rel.character2Name(), rel.affinity());
        } else {
            // New relationship — create the base record
            relationship = new CharacterRelationship();
            relationship.setCharacter1(c1.get());
            relationship.setCharacter2(c2.get());
            relationship.setType(type);
            relationship.setDescription(rel.description());
            relationship.setAffinity(rel.affinity());
            relationship.setEstablishedAtChapter(chapter.getChapterNumber());
            relationship.setCreatedAt(LocalDateTime.now());
            relationship = relationshipRepository.save(relationship);
            logger.debug("Created relationship: {} <-[{}]-> {}", rel.character1Name(), rel.type(), rel.character2Name());
        }

        // Always create a temporal snapshot for this chapter — this is the full arc record
        RelationshipState snapshot = new RelationshipState();
        snapshot.setRelationship(relationship);
        snapshot.setChapter(chapter);
        snapshot.setChapterNumber(chapter.getChapterNumber());
        snapshot.setType(type);
        snapshot.setDescription(rel.description());
        snapshot.setAffinity(rel.affinity());
        snapshot.setDynamicsNote(rel.dynamicsNote());
        snapshot.setCreatedAt(LocalDateTime.now());
        RelationshipState saved = relationshipStateRepository.save(snapshot);
        embeddingService.embedRelationshipState(saved);
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

            If a "Previously known characters" list is provided, use it as context.
            IMPORTANT: If a character appears to be a DIFFERENT individual from a known character
            that shares a generic name or description (e.g. a second "Red Goblin" that is clearly
            not the first), give them a unique disambiguating name (e.g. "Red Goblin (ch.3)").

            For each CHARACTER, determine:
            - name: The character's name as it appears in the text (use the most specific identifier available)
            - translatedName: Romanized or translated name (e.g. "Emiya Shirou" for "衛宮士郎"). Repeat if already English.
            - description: Physical appearance and notable traits observed in this chapter
            - personalityTraits: Comma-separated personality traits evident from behavior/dialogue
            - role: One of PROTAGONIST, ANTAGONIST, SUPPORTING, MINOR
            - voiceExample: One short, representative line of dialogue showing their register. Null if no dialogue.
            - emotionalState: Their emotional/mental state, e.g. "grief-stricken, resolute"
            - currentGoal: Their primary motivation right now, e.g. "find his missing sister"
            - arcStage: Their narrative arc position, e.g. "reluctant hero", "betrayed mentor", "at lowest point"
            - aliases: JSON array of ALL alternative names, titles, and epithets used for this character
                       in this chapter (e.g. ["The Magus Killer", "Emiya Kiritsugu"]). Use [] if none.
            - affiliation: The faction, guild, or organization they belong to at this chapter.
                           e.g. "Fairy Tail Guild", "Magic Council". Null if not applicable or unknown.
            - loyalty: Who or what they are loyal to — may differ from affiliation.
                       e.g. "Fiercely loyal to the King", "Secretly working against the guild".
                       Null if not determinable from this chapter.

            For each RELATIONSHIP between characters in this chapter, determine:
            - character1Name: Name of the first character
            - character2Name: Name of the second character
            - type: Current relationship type — one of ALLY, ENEMY, FAMILY, ROMANTIC, NEUTRAL, MENTOR
            - description: A label for the relationship, e.g. "Childhood friends", "Sworn enemies"
            - affinity: -1.0 (deeply hostile) to 1.0 (deeply bonded). Use 0.0 for NEUTRAL.
            - dynamicsNote: What specifically happened to this relationship IN THIS CHAPTER — key shifts,
                            revelations, turning points. e.g. "Discovered Archer is his future self,
                            shattering their alliance." Null if the relationship appears but nothing changed.

            Be thorough — include characters mentioned in dialogue even if they don't appear directly.
            Only report relationships where both characters appear or are mentioned in THIS chapter.

            Respond ONLY with valid JSON matching the requested format.
            """;
}