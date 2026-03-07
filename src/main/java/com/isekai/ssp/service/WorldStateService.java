package com.isekai.ssp.service;

import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.Character;
import com.isekai.ssp.entities.CharacterState;
import com.isekai.ssp.entities.ProjectWorldState;
import com.isekai.ssp.llm.LlmProvider;
import com.isekai.ssp.llm.LlmProviderRegistry;
import com.isekai.ssp.repository.CharacterStateRepository;
import com.isekai.ssp.repository.ProjectWorldStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maintains a compact recurrent world state after each analyzed chapter.
 *
 * After all 3 analysis steps complete, ChapterAnalysisOrchestrator fires
 * updateWorldState() asynchronously. The generated summary is stored in
 * project_world_states and injected into the NEXT chapter's extraction context
 * as a fixed-size (~350 word) block — providing faction/tension continuity and
 * deduplication safety without growing unboundedly with character count.
 *
 * Failures are non-fatal: the analysis pipeline is never blocked by world state errors.
 * The next chapter falls back to the prior snapshot or omits the block gracefully.
 */
@Service
public class WorldStateService {

    private static final Logger logger = LoggerFactory.getLogger(WorldStateService.class);

    private final ProjectWorldStateRepository worldStateRepository;
    private final CharacterStateRepository characterStateRepository;
    private final LlmProviderRegistry providerRegistry;

    public WorldStateService(
            ProjectWorldStateRepository worldStateRepository,
            CharacterStateRepository characterStateRepository,
            LlmProviderRegistry providerRegistry) {
        this.worldStateRepository = worldStateRepository;
        this.characterStateRepository = characterStateRepository;
        this.providerRegistry = providerRegistry;
    }

    /**
     * Generates an updated world state summary after a chapter is fully analyzed.
     * Runs asynchronously on aiTaskExecutor — never blocks the orchestrator.
     *
     * @param chapter           the chapter just analyzed
     * @param chapterCharacters characters returned by Step 1 (extractCharacters) —
     *                          these are the characters active in THIS chapter
     */
    @Async("aiTaskExecutor")
    public void updateWorldState(Chapter chapter, List<Character> chapterCharacters) {
        try {
            String input = buildWorldStateInput(chapter, chapterCharacters);
            LlmProvider provider = providerRegistry.resolve(null);
            String summary = provider.generate(SYSTEM_PROMPT, input);

            ProjectWorldState worldState = new ProjectWorldState();
            worldState.setProject(chapter.getProject());
            worldState.setChapterNumber(chapter.getChapterNumber());
            worldState.setSummary(summary.trim());
            worldState.setCreatedAt(LocalDateTime.now());
            worldStateRepository.save(worldState);

            logger.info("World state updated after chapter {} (project={})",
                    chapter.getChapterNumber(), chapter.getProject().getId());

        } catch (Exception e) {
            // Non-fatal: world state failure must never block the analysis pipeline.
            // The next chapter will use the prior snapshot or operate without one.
            logger.warn("World state update failed for chapter {} — next chapter will use prior snapshot: {}",
                    chapter.getChapterNumber(), e.getMessage());
        }
    }

    /**
     * Returns the most recent world state summary for a project.
     * Returns empty Optional for projects with no analyzed chapters yet.
     * Used by ContextBuilderService to inject into the extraction prompt header.
     */
    public Optional<String> getLatestSummary(Long projectId) {
        return worldStateRepository
                .findTopByProjectIdOrderByChapterNumberDesc(projectId)
                .map(ProjectWorldState::getSummary);
    }

    // -------------------------------------------------------------------------
    // Input builder
    // -------------------------------------------------------------------------

    private String buildWorldStateInput(Chapter chapter, List<Character> chapterCharacters) {
        int chapterNumber = chapter.getChapterNumber();
        Long projectId = chapter.getProject().getId();

        var sb = new StringBuilder();
        sb.append("## Chapter just analyzed: ").append(chapterNumber)
                .append(" — ").append(chapter.getTitle()).append("\n\n");

        // Active cast: characters from Step 1 with their latest state
        sb.append("## Active cast (appeared in this chapter):\n");
        for (Character c : chapterCharacters) {
            characterStateRepository.findLatestStateAtOrBefore(c.getId(), chapterNumber)
                    .ifPresentOrElse(
                            state -> sb.append(formatActiveCharacter(c, state)),
                            () -> sb.append("- ").append(c.getName())
                                    .append(c.getRole() != null ? " [" + c.getRole().name() + "]" : "")
                                    .append("\n")
                    );
        }

        // Dormant: all characters with any prior state, excluding the active cast
        Set<Long> activeIds = chapterCharacters.stream()
                .map(Character::getId)
                .collect(Collectors.toSet());

        List<CharacterState> allPriorStates =
                characterStateRepository.findProjectStatesAtOrBefore(projectId, chapterNumber - 1);

        // Deduplicate to most recent state per character (query orders DESC — first row = latest)
        Set<Long> seen = new HashSet<>();
        List<CharacterState> dormantStates = allPriorStates.stream()
                .filter(s -> !activeIds.contains(s.getCharacter().getId()))
                .filter(s -> seen.add(s.getCharacter().getId()))
                .toList();

        if (!dormantStates.isEmpty()) {
            sb.append("\n## Dormant / off-screen characters:\n");
            for (CharacterState s : dormantStates) {
                sb.append("- ").append(s.getCharacter().getName());
                if (s.getArcStage() != null) sb.append(" [").append(s.getArcStage()).append("]");
                if (s.getAffiliation() != null) sb.append(" | ").append(s.getAffiliation());
                if (s.getEmotionalState() != null) sb.append(" | last seen: ").append(s.getEmotionalState());
                sb.append(" | last seen ch.").append(s.getChapterNumber()).append("\n");
            }
        }

        // Chain prior world state for faction/tension continuity
        getLatestSummary(projectId).ifPresent(prior -> {
            sb.append("\n## Prior world state (before this chapter):\n");
            sb.append(prior).append("\n");
        });

        return sb.toString();
    }

    private String formatActiveCharacter(Character c, CharacterState state) {
        var sb = new StringBuilder();
        sb.append("- ").append(c.getName());
        if (c.getRole() != null) sb.append(" [").append(c.getRole().name()).append("]");
        if (state.getAffiliation() != null) sb.append(" | Affiliation: ").append(state.getAffiliation());
        if (state.getLoyalty() != null) sb.append(" | Loyalty: ").append(state.getLoyalty());
        if (state.getEmotionalState() != null) sb.append(" | State: ").append(state.getEmotionalState());
        if (state.getCurrentGoal() != null) sb.append(" | Goal: ").append(state.getCurrentGoal());
        if (state.getArcStage() != null) sb.append(" | Arc: ").append(state.getArcStage());
        sb.append("\n");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // System prompt
    // -------------------------------------------------------------------------

    private static final String SYSTEM_PROMPT = """
            You are a narrative state tracker for a novel translation project.
            Given character states after a chapter, produce a compact world state summary
            to be injected as context when analyzing the NEXT chapter.

            Stay within 350 words. Be dense — no filler prose.

            Never reproduce verbatim sound effects, roars, or onomatopoeia — describe them in
            narrative prose instead (e.g. "let out a roar of anguish", not the roar characters themselves).

            Use this structure exactly:

            ACTIVE FACTIONS & TENSIONS:
            [2-4 bullets: faction names and the tensions between them]

            ACTIVE CHARACTERS (this chapter):
            [One line each: Name — role — current state — key goal — affiliation]

            DORMANT CHARACTERS (not this chapter):
            [One line each: Name — last known status — last seen ch.N]

            UNRESOLVED NARRATIVE THREADS:
            [2-5 bullets: open plot questions, unresolved conflicts, brewing tensions]

            RELATIONSHIP SHIFTS THIS CHAPTER:
            [1-3 bullets: significant relationship changes, or "None"]

            Output the structured summary only. No preamble, no markdown beyond the section labels above.
            """;
}
