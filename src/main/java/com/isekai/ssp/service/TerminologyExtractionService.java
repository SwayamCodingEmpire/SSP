package com.isekai.ssp.service;

import com.isekai.ssp.dto.TerminologyExtractionResult;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.Glossary;
import com.isekai.ssp.helpers.GlossaryType;
import com.isekai.ssp.llm.LlmProvider;
import com.isekai.ssp.llm.LlmProviderRegistry;
import com.isekai.ssp.repository.GlossaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts domain-specific terminology from academic/technical content.
 * Populates the glossary with scientific terms, abbreviations, and definitions.
 */
@Service
public class TerminologyExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(TerminologyExtractionService.class);

    private final LlmProviderRegistry providerRegistry;
    private final GlossaryRepository glossaryRepository;
    private final NarrativeEmbeddingService embeddingService;

    public TerminologyExtractionService(
            LlmProviderRegistry providerRegistry,
            GlossaryRepository glossaryRepository,
            NarrativeEmbeddingService embeddingService) {
        this.providerRegistry = providerRegistry;
        this.glossaryRepository = glossaryRepository;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public List<Glossary> extractTerminology(Chapter chapter) {
        BeanOutputConverter<TerminologyExtractionResult> converter =
                new BeanOutputConverter<>(TerminologyExtractionResult.class);

        try {
            LlmProvider provider = providerRegistry.resolve(null);
            String context = "## Chapter %d: %s\n\n%s".formatted(
                    chapter.getChapterNumber(), chapter.getTitle(), chapter.getOriginalText());

            String response = provider.generate(
                    SYSTEM_PROMPT,
                    context + "\n\n" + converter.getFormat()
            );

            TerminologyExtractionResult result = converter.convert(response);
            List<Glossary> terms = new ArrayList<>();

            for (TerminologyExtractionResult.DetectedTerm detected : result.terms()) {
                // Check if term already exists in project glossary
                var existing = glossaryRepository.findByProjectIdAndOriginalTerm(
                        chapter.getProject().getId(), detected.term());
                if (existing.isPresent()) {
                    continue;
                }

                Glossary glossary = new Glossary();
                glossary.setProject(chapter.getProject());
                glossary.setOriginalTerm(detected.term());
                glossary.setTranslatedTerm(detected.term()); // placeholder — needs translation
                glossary.setType(parseGlossaryType(detected.category()));
                glossary.setContextNotes(detected.definition());
                glossary.setEnforceConsistency(true);
                glossary.setCreatedAt(LocalDateTime.now());

                Glossary saved = glossaryRepository.save(glossary);
                terms.add(saved);
                embeddingService.embedGlossaryTerm(saved);
            }

            return terms;

        } catch (Exception e) {
            throw new AiServiceException("primary", "terminology-extraction",
                    "Failed to extract terminology for chapter " + chapter.getId(), e);
        }
    }

    private GlossaryType parseGlossaryType(String category) {
        try { return GlossaryType.valueOf(category); }
        catch (Exception e) { return GlossaryType.SCIENTIFIC_TERM; }
    }

    private static final String SYSTEM_PROMPT = """
            You are a terminology extraction specialist. Given an academic or technical text,
            identify domain-specific terms that need consistent translation.

            For each term, determine:
            - term: The exact term as it appears in the text
            - definition: A brief definition or explanation of the term
            - abbreviation: The abbreviation if one is used (null otherwise)
            - category: One of SCIENTIFIC_TERM, ABBREVIATION, FORMULA, CITATION_STYLE, METHODOLOGY, PROPER_NOUN, FOREIGN_WORD
            - crossReferences: Other terms in the text that this term relates to

            Focus on terms that:
            - Are specific to the domain and might be translated incorrectly by general translation
            - Have established translations that must be used consistently
            - Are abbreviations that need to be expanded for clarity

            Respond ONLY with valid JSON matching the requested format.
            """;
}
