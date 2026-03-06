package com.isekai.ssp.service;

import com.isekai.ssp.dto.SectionAnalysisResult;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.ContentSection;
import com.isekai.ssp.helpers.SectionType;
import com.isekai.ssp.llm.LlmProvider;
import com.isekai.ssp.llm.LlmProviderRegistry;
import com.isekai.ssp.repository.ContentSectionRepository;
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
 * Analyzes academic/technical/non-fiction content for section structure.
 * Detects sections (intro, methodology, results, conclusion), key concepts,
 * and structural organization.
 */
@Service
public class SectionAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(SectionAnalysisService.class);

    private final LlmProviderRegistry providerRegistry;
    private final ContentSectionRepository sectionRepository;
    private final NarrativeEmbeddingService embeddingService;

    public SectionAnalysisService(
            LlmProviderRegistry providerRegistry,
            ContentSectionRepository sectionRepository,
            NarrativeEmbeddingService embeddingService) {
        this.providerRegistry = providerRegistry;
        this.sectionRepository = sectionRepository;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public List<ContentSection> analyzeSections(Chapter chapter) {
        BeanOutputConverter<SectionAnalysisResult> converter =
                new BeanOutputConverter<>(SectionAnalysisResult.class);

        try {
            LlmProvider provider = providerRegistry.resolve(null);
            String context = "## Chapter %d: %s\n\n%s".formatted(
                    chapter.getChapterNumber(), chapter.getTitle(), chapter.getOriginalText());

            String response = provider.generate(
                    SYSTEM_PROMPT,
                    context + "\n\n" + converter.getFormat()
            );

            SectionAnalysisResult result = converter.convert(response);
            List<ContentSection> sections = new ArrayList<>();

            for (SectionAnalysisResult.DetectedSection detected : result.sections()) {
                ContentSection section = new ContentSection();
                section.setProject(chapter.getProject());
                section.setChapter(chapter);
                section.setType(parseSectionType(detected.type()));
                section.setTitle(detected.title());
                section.setSummary(detected.summary());
                section.setSequenceNumber(detected.sequenceNumber());
                section.setKeyConceptsJson(toJson(detected.keyConcepts()));
                section.setCreatedAt(LocalDateTime.now());

                ContentSection saved = sectionRepository.save(section);
                sections.add(saved);
                embeddingService.embedContentSection(saved);
            }

            return sections;

        } catch (Exception e) {
            throw new AiServiceException("primary", "section-analysis",
                    "Failed to analyze sections for chapter " + chapter.getId(), e);
        }
    }

    private SectionType parseSectionType(String type) {
        try { return SectionType.valueOf(type); }
        catch (Exception e) { return SectionType.INTRODUCTION; }
    }

    private String toJson(List<String> list) {
        try {
            return new ObjectMapper().writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static final String SYSTEM_PROMPT = """
            You are an academic text analyst. Given a chapter or section of a document,
            identify its structural sections.

            For each section, determine:
            - type: One of INTRODUCTION, METHODOLOGY, RESULTS, DISCUSSION, CONCLUSION,
              EXAMPLE, EXERCISE, DEFINITION, PROOF, CASE_STUDY, ABSTRACT, BIBLIOGRAPHY, APPENDIX
            - title: The section heading or a descriptive title
            - summary: 1-2 sentence summary of the section content
            - sequenceNumber: Position in the document (1, 2, 3...)
            - keyConcepts: Key terms, concepts, or ideas introduced in this section

            Respond ONLY with valid JSON matching the requested format.
            """;
}
