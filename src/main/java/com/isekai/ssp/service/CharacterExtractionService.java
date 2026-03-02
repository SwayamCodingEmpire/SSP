package com.isekai.ssp.service;

import com.isekai.ssp.dto.CharacterExtractionResult;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.Character;
import com.isekai.ssp.helpers.CharacterRole;
import com.isekai.ssp.repository.CharacterRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CharacterExtractionService {

    private final ChatClient chatClient;
    private final CharacterRepository characterRepository;
    private final ContextBuilderService contextBuilder;

    public CharacterExtractionService(
            ChatClient chatClient,
            CharacterRepository characterRepository,
            ContextBuilderService contextBuilder) {
        this.chatClient = chatClient;
        this.characterRepository = characterRepository;
        this.contextBuilder = contextBuilder;
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

            return result.characters().stream()
                    .map(extracted -> upsertCharacter(chapter.getProject(), extracted, chapter.getChapterNumber()))
                    .toList();

        } catch (Exception e) {
            throw new AiServiceException("primary", "character-extraction",
                    "Failed to extract characters for chapter " + chapter.getId(), e);
        }
    }

    private Character upsertCharacter(
            com.isekai.ssp.entities.Project project,
            CharacterExtractionResult.ExtractedCharacter extracted,
            int chapterNumber) {

        return characterRepository
                .findByProjectIdAndName(project.getId(), extracted.name())
                .map(existing -> {
                    if (extracted.description() != null && !extracted.description().isBlank()) {
                        existing.setDescription(extracted.description());
                    }
                    if (extracted.personalityTraits() != null) {
                        existing.setPersonalityTraits(extracted.personalityTraits());
                    }
                    existing.setUpdatedAt(LocalDateTime.now());
                    return characterRepository.save(existing);
                })
                .orElseGet(() -> {
                    Character c = new Character();
                    c.setProject(project);
                    c.setName(extracted.name());
                    c.setDescription(extracted.description());
                    c.setPersonalityTraits(extracted.personalityTraits());
                    c.setRole(parseRole(extracted.role()));
                    c.setFirstAppearanceChapter(chapterNumber);
                    c.setCreatedAt(LocalDateTime.now());
                    return characterRepository.save(c);
                });
    }

    private CharacterRole parseRole(String role) {
        try { return CharacterRole.valueOf(role); }
        catch (Exception e) { return CharacterRole.SUPPORTING; }
    }

    private static final String SYSTEM_PROMPT = """
            You are a literary analyst specializing in character identification.
            Given a chapter from a novel, identify ALL characters who appear or are mentioned.

            For each character, determine:
            - name: The character's name as it appears in the text (use original language name)
            - description: Physical appearance, notable traits observed in this chapter
            - personalityTraits: Comma-separated personality traits evident from behavior/dialogue
            - role: One of PROTAGONIST, ANTAGONIST, SUPPORTING, MINOR

            Be thorough — include characters mentioned in dialogue even if they don't appear.
            If previously known characters are listed, only include new or updated characters.

            Respond ONLY with valid JSON matching the requested format.
            """;
}
