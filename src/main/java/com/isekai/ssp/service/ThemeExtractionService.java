package com.isekai.ssp.service;

import com.isekai.ssp.dto.ThemeExtractionResult;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.ThematicElement;
import com.isekai.ssp.llm.LlmProvider;
import com.isekai.ssp.llm.LlmProviderRegistry;
import com.isekai.ssp.repository.ThematicElementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts themes, symbols, motifs, and allusions from poetry, essays,
 * and non-fiction content.
 */
@Service
public class ThemeExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(ThemeExtractionService.class);

    private final LlmProviderRegistry providerRegistry;
    private final ThematicElementRepository thematicElementRepository;
    private final NarrativeEmbeddingService embeddingService;

    public ThemeExtractionService(
            LlmProviderRegistry providerRegistry,
            ThematicElementRepository thematicElementRepository,
            NarrativeEmbeddingService embeddingService) {
        this.providerRegistry = providerRegistry;
        this.thematicElementRepository = thematicElementRepository;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public List<ThematicElement> extractThemes(Chapter chapter) {
        BeanOutputConverter<ThemeExtractionResult> converter =
                new BeanOutputConverter<>(ThemeExtractionResult.class);

        try {
            LlmProvider provider = providerRegistry.resolve(null);
            String context = "## Chapter %d: %s\n\n%s".formatted(
                    chapter.getChapterNumber(), chapter.getTitle(), chapter.getOriginalText());

            String response = provider.generate(
                    SYSTEM_PROMPT,
                    context + "\n\n" + converter.getFormat()
            );

            ThemeExtractionResult result = converter.convert(response);
            List<ThematicElement> elements = new ArrayList<>();

            for (ThemeExtractionResult.DetectedTheme detected : result.themes()) {
                ThematicElement element = new ThematicElement();
                element.setProject(chapter.getProject());
                element.setChapter(chapter);
                element.setThemeType(detected.themeType());
                element.setName(detected.name());
                element.setDescription(detected.description());
                element.setOccurrencesJson(toJson(detected.occurrences()));
                element.setCreatedAt(LocalDateTime.now());

                ThematicElement saved = thematicElementRepository.save(element);
                elements.add(saved);
                embeddingService.embedThematicElement(saved);
            }

            return elements;

        } catch (Exception e) {
            throw new AiServiceException("primary", "theme-extraction",
                    "Failed to extract themes for chapter " + chapter.getId(), e);
        }
    }

    private String toJson(List<String> list) {
        try {
            return new ObjectMapper().writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static final String SYSTEM_PROMPT = """
            You are a literary and thematic analyst. Given a text, identify its themes,
            symbols, motifs, and allusions.

            For each thematic element, determine:
            - themeType: One of "THEME", "SYMBOL", "MOTIF", "ALLUSION", "IMAGERY"
            - name: A concise label for the element
            - description: Explanation of its significance and how it functions in the text
            - occurrences: Specific passages or lines where this element appears

            Respond ONLY with valid JSON matching the requested format.
            """;
}
