package com.isekai.ssp.service;

import com.isekai.ssp.dto.SpeakerDetectionResult;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.Character;
import com.isekai.ssp.entities.CharacterState;
import com.isekai.ssp.llm.LlmProvider;
import com.isekai.ssp.llm.LlmProviderRegistry;
import com.isekai.ssp.repository.CharacterRepository;
import com.isekai.ssp.repository.CharacterStateRepository;
import com.isekai.ssp.repository.ChapterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Uses AI to analyze dialogue patterns at the chapter level.
 * Identifies which characters speak, their emotional states, and dialogue summaries.
 */
@Service
public class SpeakerDetectionService {

    private final LlmProviderRegistry providerRegistry;
    private final CharacterRepository characterRepository;
    private final CharacterStateRepository characterStateRepository;
    private final ChapterRepository chapterRepository;
    private final ContextBuilderService contextBuilder;
    private final NarrativeEmbeddingService embeddingService;
    private final Logger logger = LoggerFactory.getLogger(SpeakerDetectionService.class);

    public SpeakerDetectionService(
            LlmProviderRegistry providerRegistry,
            CharacterRepository characterRepository,
            CharacterStateRepository characterStateRepository,
            ChapterRepository chapterRepository,
            ContextBuilderService contextBuilder,
            NarrativeEmbeddingService embeddingService) {
        this.providerRegistry = providerRegistry;
        this.characterRepository = characterRepository;
        this.characterStateRepository = characterStateRepository;
        this.chapterRepository = chapterRepository;
        this.contextBuilder = contextBuilder;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public SpeakerDetectionResult detectSpeakers(Chapter chapter) {
        List<Character> characters = characterRepository
                .findByProjectId(chapter.getProject().getId());

        if (characters.isEmpty()) {
            return new SpeakerDetectionResult(List.of());
        }

        String context = contextBuilder.buildSpeakerDetectionContext(chapter, characters);

        BeanOutputConverter<SpeakerDetectionResult> converter =
                new BeanOutputConverter<>(SpeakerDetectionResult.class);

        try {
            LlmProvider provider = providerRegistry.resolve(null);
            String response = provider.generate(
                    SYSTEM_PROMPT,
                    context + "\n\n" + converter.getFormat()
            );

            SpeakerDetectionResult result = converter.convert(response);
            persistDialogueData(chapter, result);
            return result;

        } catch (Exception e) {
            throw new AiServiceException("primary", "speaker-detection",
                    "Failed to detect speakers for chapter " + chapter.getId(), e);
        }
    }

    /**
     * Writes dialogue emotion + summary back into each character's CharacterState for this chapter.
     * Re-embeds the state so pgvector gets the richer temporal voice profile.
     */
    private void persistDialogueData(Chapter chapter, SpeakerDetectionResult result) {
        for (SpeakerDetectionResult.CharacterDialogue dialogue : result.characterDialogues()) {
            Optional<Character> characterOpt = characterRepository
                    .findByProjectIdAndName(chapter.getProject().getId(), dialogue.characterName());

            if (characterOpt.isEmpty()) {
                logger.warn("Speaker detection: character '{}' not found in project — skipping",
                        dialogue.characterName());
                continue;
            }

            Optional<CharacterState> stateOpt = characterStateRepository
                    .findByCharacterIdAndChapterId(characterOpt.get().getId(), chapter.getId());

            if (stateOpt.isEmpty()) {
                logger.warn("Speaker detection: no CharacterState found for '{}' at chapter {} — skipping",
                        dialogue.characterName(), chapter.getChapterNumber());
                continue;
            }

            CharacterState state = stateOpt.get();
            state.setDialogueEmotionType(dialogue.emotionType());
            state.setDialogueEmotionIntensity(dialogue.emotionIntensity());
            state.setDialogueSummary(dialogue.dialogueSummary());
            characterStateRepository.save(state);

            // Re-embed so pgvector gets the richer temporal voice profile
            embeddingService.embedCharacterState(state);

            logger.debug("Persisted dialogue data for '{}' at chapter {} (emotion={}, intensity={})",
                    dialogue.characterName(), chapter.getChapterNumber(),
                    dialogue.emotionType(), dialogue.emotionIntensity());
        }
    }

    private static final String SYSTEM_PROMPT = """
            You are a literary analyst specializing in dialogue attribution.
            Given a chapter from a novel and a list of known characters,
            analyze the dialogue patterns and identify which characters speak.

            For each character who has dialogue in this chapter, provide:
            - characterName: Exact name from the character list
            - emotionType: Dominant emotion — one of NEUTRAL, HAPPY, SAD, ANGRY, FEARFUL, URGENT, EXCITED, CONTEMPLATIVE
            - emotionIntensity: 0.0 (barely perceptible) to 1.0 (overwhelming)
            - dialogueSummary: Brief summary of what this character says and how they say it

            Only include characters who actually speak in this chapter.

            Respond ONLY with valid JSON matching the requested format.
            """;
}
