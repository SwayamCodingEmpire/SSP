package com.isekai.ssp.service;

import com.isekai.ssp.dto.FormAnalysisResult;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.PoeticForm;
import com.isekai.ssp.helpers.VerseForm;
import com.isekai.ssp.llm.LlmProvider;
import com.isekai.ssp.llm.LlmProviderRegistry;
import com.isekai.ssp.repository.PoeticFormRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Analyzes poetry for verse form, meter, rhyme scheme, and stanza structure.
 */
@Service
public class FormAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(FormAnalysisService.class);

    private final LlmProviderRegistry providerRegistry;
    private final PoeticFormRepository poeticFormRepository;
    private final NarrativeEmbeddingService embeddingService;

    public FormAnalysisService(
            LlmProviderRegistry providerRegistry,
            PoeticFormRepository poeticFormRepository,
            NarrativeEmbeddingService embeddingService) {
        this.providerRegistry = providerRegistry;
        this.poeticFormRepository = poeticFormRepository;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public PoeticForm analyzeForm(Chapter chapter) {
        BeanOutputConverter<FormAnalysisResult> converter =
                new BeanOutputConverter<>(FormAnalysisResult.class);

        try {
            LlmProvider provider = providerRegistry.resolve(null);
            String context = "## Chapter %d: %s\n\n%s".formatted(
                    chapter.getChapterNumber(), chapter.getTitle(), chapter.getOriginalText());

            String response = provider.generate(
                    SYSTEM_PROMPT,
                    context + "\n\n" + converter.getFormat()
            );

            FormAnalysisResult result = converter.convert(response);

            PoeticForm form = new PoeticForm();
            form.setProject(chapter.getProject());
            form.setChapter(chapter);
            form.setForm(parseVerseForm(result.form()));
            form.setMeterPattern(result.meterPattern());
            form.setRhymeScheme(result.rhymeScheme());
            form.setStanzaCount(result.stanzaCount());
            form.setLinesPerStanza(result.linesPerStanza());
            form.setSoundDevicesJson(toJson(result.soundDevices()));
            form.setNotes(result.notes());
            form.setCreatedAt(LocalDateTime.now());

            PoeticForm saved = poeticFormRepository.save(form);
            embeddingService.embedPoeticForm(saved);
            return saved;

        } catch (Exception e) {
            throw new AiServiceException("primary", "form-analysis",
                    "Failed to analyze poetic form for chapter " + chapter.getId(), e);
        }
    }

    private VerseForm parseVerseForm(String form) {
        try { return VerseForm.valueOf(form); }
        catch (Exception e) { return VerseForm.FREE_VERSE; }
    }

    private String toJson(java.util.List<String> list) {
        try {
            return new ObjectMapper().writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static final String SYSTEM_PROMPT = """
            You are a poetry analyst and prosodist. Given a poem or collection of verse,
            analyze its formal structure.

            Determine:
            - form: One of SONNET, HAIKU, FREE_VERSE, BLANK_VERSE, LIMERICK, BALLAD, ODE, ELEGY, VILLANELLE, GHAZAL, CUSTOM
            - meterPattern: The predominant meter (e.g., "iambic pentameter", "trochaic tetrameter", "mixed/free")
            - rhymeScheme: The rhyme scheme pattern (e.g., "ABAB CDCD EFEF GG" for a Shakespearean sonnet, "none" for free verse)
            - stanzaCount: Number of stanzas
            - linesPerStanza: Lines per stanza (null if irregular)
            - soundDevices: Notable sound devices used (alliteration, assonance, consonance, onomatopoeia, etc.)
            - notes: Additional observations about the form (enjambment patterns, caesura usage, etc.)

            Respond ONLY with valid JSON matching the requested format.
            """;
}
