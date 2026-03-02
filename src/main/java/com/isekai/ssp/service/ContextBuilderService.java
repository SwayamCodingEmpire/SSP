package com.isekai.ssp.service;

import com.isekai.ssp.entities.*;
import com.isekai.ssp.entities.Character;
import com.isekai.ssp.repository.CharacterRepository;
import com.isekai.ssp.repository.GlossaryRepository;
import com.isekai.ssp.repository.SceneRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Assembles context blocks for AI prompts.
 * Works with full chapter text — no segment-level processing.
 */
@Service
public class ContextBuilderService {

    private final CharacterRepository characterRepository;
    private final GlossaryRepository glossaryRepository;
    private final SceneRepository sceneRepository;

    public ContextBuilderService(
            CharacterRepository characterRepository,
            GlossaryRepository glossaryRepository,
            SceneRepository sceneRepository) {
        this.characterRepository = characterRepository;
        this.glossaryRepository = glossaryRepository;
        this.sceneRepository = sceneRepository;
    }

    public String buildCharacterExtractionContext(Chapter chapter) {
        var sb = new StringBuilder();
        sb.append("## Chapter %d: %s\n\n".formatted(chapter.getChapterNumber(), chapter.getTitle()));

        List<Character> existing = characterRepository.findByProjectId(chapter.getProject().getId());
        if (!existing.isEmpty()) {
            sb.append("## Previously known characters in this project:\n");
            for (Character c : existing) {
                sb.append("- %s (%s): %s\n".formatted(c.getName(), c.getRole(), c.getDescription()));
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

    public TranslationContext buildTranslationContext(Chapter chapter, String textToTranslate) {
        Project project = chapter.getProject();
        Long projectId = project.getId();

        List<Glossary> glossaryTerms = glossaryRepository
                .findByProjectIdAndEnforceConsistencyTrue(projectId);
        List<Character> characters = characterRepository.findByProjectId(projectId);
        List<Scene> scenes = sceneRepository.findByChapterId(chapter.getId());

        return new TranslationContext(
                project.getSourceLanguage(),
                project.getTargetLanguage(),
                formatGlossary(glossaryTerms),
                formatCharacters(characters),
                formatSceneContext(scenes),
                project.getTranslationStyle() != null ? project.getTranslationStyle() : "",
                textToTranslate
        );
    }

    private String formatSceneContext(List<Scene> scenes) {
        if (scenes.isEmpty()) return "";
        return scenes.stream()
                .map(s -> {
                    var sb = new StringBuilder();
                    if (s.getType() != null)         sb.append("Type: ").append(s.getType().name());
                    if (s.getTone() != null)         sb.append(" | Tone: ").append(s.getTone().name());
                    if (s.getPace() != null)         sb.append(" | Pace: ").append(s.getPace().name());
                    if (s.getTensionLevel() != null) sb.append(" | Tension: ").append(s.getTensionLevel());
                    if (s.getSummary() != null)      sb.append(" | ").append(s.getSummary());
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
                .map(c -> "- %s (%s): %s | Traits: %s".formatted(
                        c.getName(),
                        c.getRole() != null ? c.getRole().name() : "UNKNOWN",
                        c.getDescription() != null ? c.getDescription() : "No description",
                        c.getPersonalityTraits() != null ? c.getPersonalityTraits() : "unknown"))
                .collect(Collectors.joining("\n"));
    }

    public record TranslationContext(
            String sourceLanguage,
            String targetLanguage,
            String glossaryBlock,
            String characterBlock,
            String sceneContext,   // tone/pace/tension/type from SceneAnalysisService
            String styleGuide,    // project-level prose style descriptor
            String textToTranslate
    ) {}
}
