package com.isekai.ssp.domain.strategies;

import com.isekai.ssp.domain.*;
import com.isekai.ssp.helpers.ContentFamily;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Strategy for academic/technical content: textbooks, journals, articles.
 * Prioritizes terminology precision, clarity, logical flow, and citation integrity.
 * No character analysis — uses terminology extraction and section analysis instead.
 */
@Component
public class AcademicStrategy implements DomainStrategy {

    @Override
    public ContentFamily getFamily() {
        return ContentFamily.ACADEMIC;
    }

    @Override
    public List<AnalysisStep> getAnalysisPipeline() {
        return List.of(
                AnalysisStep.TERMINOLOGY_EXTRACTION,
                AnalysisStep.SECTION_ANALYSIS
        );
    }

    @Override
    public String buildPass1SystemPrompt(DomainContext ctx) {
        return """
                You are a professional academic translator from %s to %s,
                specializing in scholarly and technical texts.
                Your ONLY goal in this pass is ACCURACY, PRECISION, and COMPLETENESS.

                RULES:
                - Translate every sentence. Miss nothing.
                - Preserve ALL citations, references, and cross-references exactly as they appear.
                - Preserve ALL formulas, equations, and mathematical notation unchanged.
                - Maintain numbered lists, bullet points, and table structures.
                - Use the enforced glossary terms below without any deviation.
                - Technical terms must be translated consistently throughout — same source term → same target term.
                - Preserve abbreviations in their original form on first occurrence, with translated expansion.
                - Do NOT restructure arguments or simplify. Preserve the author's logical flow exactly.
                - If a term has no established translation, keep the original with a [bracketed translation].

                ## Enforced glossary
                %s

                ## Domain terminology
                %s
                """.formatted(
                ctx.sourceLanguage(), ctx.targetLanguage(),
                ctx.glossaryBlock(),
                ctx.terminologyBlock() != null ? ctx.terminologyBlock() : "(No domain terms extracted yet)");
    }

    @Override
    public String buildPass1UserPrompt(DomainContext ctx) {
        return """
                Translate the following academic text faithfully from %s to %s.
                Preserve every sentence, every citation, every formula, every cross-reference.
                Output only the translated text — no commentary, no formatting markers.

                ## Text:
                %s
                """.formatted(ctx.sourceLanguage(), ctx.targetLanguage(), ctx.textToTranslate());
    }

    @Override
    public String buildPass2SystemPrompt(DomainContext ctx) {
        return """
                You are a senior academic editor and translator working from %s to %s.
                Your task is to refine an accurate draft into publication-quality academic prose.

                Your goal is CLARITY WITHOUT LOSS OF PRECISION — the translated text should read as if
                originally written in %s by a subject-matter expert, while preserving every factual claim
                and logical connection from the source.

                ## Editorial principles
                - CLARITY:       Untangle complex sentence structures where the target language benefits from restructuring.
                - PRECISION:     Never sacrifice accuracy for readability. If a complex sentence is precise, keep it complex.
                - CONSISTENCY:   Ensure the same term is used uniformly throughout. No synonymic variation for technical terms.
                - FLOW:          Improve paragraph transitions and logical connectives (however, therefore, consequently).
                - REGISTER:      Maintain formal academic register. No colloquialisms, no simplification for lay readers.
                - CONCISION:     Remove redundancy common in source-language academic style when it clutters in the target.

                ## Structure preservation — non-negotiable
                - Preserve ALL section headings, numbered items, and hierarchical structure.
                - Preserve ALL citations in their original format (APA, MLA, Chicago, etc.).
                - Preserve ALL footnotes and endnotes with their numbering.
                - Preserve ALL table and figure references (Table 1, Figure 3, etc.).
                - Preserve ALL mathematical formulas and equations exactly.

                %s
                ## Enforced glossary (never deviate from these terms)
                %s

                ## Domain terminology
                %s
                %s
                Respond ONLY with valid JSON matching the requested format.
                """.formatted(
                ctx.sourceLanguage(), ctx.targetLanguage(), ctx.targetLanguage(),
                buildStyleSection(ctx.styleGuide()),
                ctx.glossaryBlock(),
                ctx.terminologyBlock() != null ? ctx.terminologyBlock() : "(No domain terms extracted yet)",
                buildStyleExamplesSection(ctx.styleExamplesBlock()));
    }

    @Override
    public String buildPass2UserPrompt(DomainContext ctx, String faithfulDraft) {
        return """
                ## Source text (original %s — for logical structure and precise meaning):
                %s

                ## Faithful draft (accurate but may need editorial polish):
                %s

                Refine the faithful draft into publication-quality academic prose in %s.
                Ensure terminology consistency, logical flow, and formal register.
                Preserve all citations, formulas, and structural elements exactly.
                Provide brief contextNotes on any significant editorial decisions.
                """.formatted(
                ctx.sourceLanguage(), ctx.textToTranslate(),
                faithfulDraft,
                ctx.targetLanguage());
    }

    @Override
    public Set<ContextType> getRequiredContextTypes() {
        return Set.of(
                ContextType.GLOSSARY,
                ContextType.TERMINOLOGY,
                ContextType.STYLE_EXAMPLES,
                ContextType.TRANSLATION_MEMORY
        );
    }

    @Override
    public List<String> getContentElementTypes() {
        return List.of("INTRODUCTION", "METHODOLOGY", "RESULTS", "DISCUSSION", "CONCLUSION",
                "EXAMPLE", "EXERCISE", "DEFINITION", "PROOF", "CASE_STUDY");
    }

    @Override
    public List<String> getGlossaryCategories() {
        return List.of("SCIENTIFIC_TERM", "ABBREVIATION", "FORMULA", "CITATION_STYLE",
                "METHODOLOGY", "PROPER_NOUN", "FOREIGN_WORD");
    }

    @Override
    public List<QualityDimension> getQualityDimensions() {
        return List.of(
                QualityDimension.ACCURACY,
                QualityDimension.TERMINOLOGY,
                QualityDimension.FLUENCY,
                QualityDimension.STYLE
        );
    }

    private String buildStyleSection(String styleGuide) {
        if (styleGuide == null || styleGuide.isBlank()) return "";
        return "## Project style guide\n" + styleGuide + "\n";
    }

    private String buildStyleExamplesSection(String styleExamplesBlock) {
        if (styleExamplesBlock == null || styleExamplesBlock.isBlank()) return "";
        return """
                ## Approved translation examples
                Study these to understand the target academic style for this project.

                %s

                """.formatted(styleExamplesBlock);
    }
}
