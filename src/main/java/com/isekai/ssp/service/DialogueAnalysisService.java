package com.isekai.ssp.service;

import com.isekai.ssp.dto.DialogueAnalysisResult;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.ScriptElement;
import com.isekai.ssp.helpers.ScriptElementType;
import com.isekai.ssp.llm.LlmProvider;
import com.isekai.ssp.llm.LlmProviderRegistry;
import com.isekai.ssp.repository.ScriptElementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes script content for dialogue structure: scene headings, character cues,
 * dialogue blocks, stage directions, transitions.
 */
@Service
public class DialogueAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(DialogueAnalysisService.class);

    private final LlmProviderRegistry providerRegistry;
    private final ScriptElementRepository scriptElementRepository;
    private final NarrativeEmbeddingService embeddingService;

    public DialogueAnalysisService(
            LlmProviderRegistry providerRegistry,
            ScriptElementRepository scriptElementRepository,
            NarrativeEmbeddingService embeddingService) {
        this.providerRegistry = providerRegistry;
        this.scriptElementRepository = scriptElementRepository;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public List<ScriptElement> analyzeDialogue(Chapter chapter) {
        BeanOutputConverter<DialogueAnalysisResult> converter =
                new BeanOutputConverter<>(DialogueAnalysisResult.class);

        try {
            LlmProvider provider = providerRegistry.resolve(null);
            String context = "## Chapter %d: %s\n\n%s".formatted(
                    chapter.getChapterNumber(), chapter.getTitle(), chapter.getOriginalText());

            String response = provider.generate(
                    SYSTEM_PROMPT,
                    context + "\n\n" + converter.getFormat()
            );

            DialogueAnalysisResult result = converter.convert(response);
            List<ScriptElement> elements = new ArrayList<>();

            for (DialogueAnalysisResult.DetectedElement detected : result.elements()) {
                ScriptElement element = new ScriptElement();
                element.setProject(chapter.getProject());
                element.setChapter(chapter);
                element.setType(parseElementType(detected.type()));
                element.setContent(detected.content());
                element.setCharacterName(detected.characterName());
                element.setSequenceNumber(detected.sequenceNumber());
                element.setParenthetical(detected.parenthetical());
                element.setNotes(detected.notes());
                element.setCreatedAt(LocalDateTime.now());

                ScriptElement saved = scriptElementRepository.save(element);
                elements.add(saved);
                embeddingService.embedScriptElement(saved);
            }

            return elements;

        } catch (Exception e) {
            throw new AiServiceException("primary", "dialogue-analysis",
                    "Failed to analyze dialogue for chapter " + chapter.getId(), e);
        }
    }

    private ScriptElementType parseElementType(String type) {
        try { return ScriptElementType.valueOf(type); }
        catch (Exception e) { return ScriptElementType.DIALOGUE; }
    }

    private static final String SYSTEM_PROMPT = """
            You are a script analyst specializing in screenplay/teleplay structure.
            Given a script or script excerpt, parse it into structural elements.

            For each element, determine:
            - type: One of SCENE_HEADING, ACTION_LINE, CHARACTER_CUE, DIALOGUE, PARENTHETICAL, TRANSITION, MONTAGE
            - content: The actual text of the element
            - characterName: The speaking character (for DIALOGUE and CHARACTER_CUE elements, null otherwise)
            - sequenceNumber: Position in the script (1, 2, 3...)
            - parenthetical: Any parenthetical direction for this dialogue (null if none)
            - notes: Any notable observations (e.g., "overlapping dialogue", "O.S.", "V.O.")

            Respond ONLY with valid JSON matching the requested format.
            """;
}
