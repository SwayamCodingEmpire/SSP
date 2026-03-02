package com.isekai.ssp.service;

import com.isekai.ssp.dto.SpeakerDetectionResult;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.Character;
import com.isekai.ssp.repository.CharacterRepository;
import com.isekai.ssp.repository.ChapterRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Uses AI to analyze dialogue patterns at the chapter level.
 * Identifies which characters speak, their emotional states, and dialogue summaries.
 */
@Service
public class SpeakerDetectionService {

    private final ChatClient chatClient;
    private final CharacterRepository characterRepository;
    private final ChapterRepository chapterRepository;
    private final ContextBuilderService contextBuilder;

    public SpeakerDetectionService(
            ChatClient chatClient,
            CharacterRepository characterRepository,
            ChapterRepository chapterRepository,
            ContextBuilderService contextBuilder) {
        this.chatClient = chatClient;
        this.characterRepository = characterRepository;
        this.chapterRepository = chapterRepository;
        this.contextBuilder = contextBuilder;
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
            String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(context + "\n\n" + converter.getFormat())
                    .call()
                    .content();

            return converter.convert(response);

        } catch (Exception e) {
            throw new AiServiceException("primary", "speaker-detection",
                    "Failed to detect speakers for chapter " + chapter.getId(), e);
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
