package com.isekai.ssp.service;

import com.isekai.ssp.dto.SceneAnalysisResult;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.Scene;
import com.isekai.ssp.helpers.EmotionalTone;
import com.isekai.ssp.helpers.NarrativePace;
import com.isekai.ssp.helpers.NarrativeTimeType;
import com.isekai.ssp.helpers.SceneType;
import com.isekai.ssp.repository.SceneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SceneAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(SceneAnalysisService.class);

    private final ChatClient chatClient;
    private final SceneRepository sceneRepository;
    private final ContextBuilderService contextBuilder;
    private final NarrativeEmbeddingService embeddingService;

    public SceneAnalysisService(
            ChatClient chatClient,
            SceneRepository sceneRepository,
            ContextBuilderService contextBuilder,
            NarrativeEmbeddingService embeddingService) {
        this.chatClient = chatClient;
        this.sceneRepository = sceneRepository;
        this.contextBuilder = contextBuilder;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public List<Scene> analyzeScenes(Chapter chapter) {
        String context = contextBuilder.buildSceneAnalysisContext(chapter);

        BeanOutputConverter<SceneAnalysisResult> converter =
                new BeanOutputConverter<>(SceneAnalysisResult.class);

        try {
            String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(context + "\n\n" + converter.getFormat())
                    .call()
                    .content();

            SceneAnalysisResult result = converter.convert(response);

            List<Scene> scenes = new ArrayList<>();
            for (SceneAnalysisResult.DetectedScene detected : result.scenes()) {
                Scene scene;

                if (detected.continuedFromPrevious()) {
                    scene = findExistingScene(chapter, detected);
                    if (scene != null) {
                        scene.getChapters().add(chapter);
                        sceneRepository.save(scene);
                        scenes.add(scene);
                        embeddingService.embedScene(scene);
                        continue;
                    }
                }

                scene = new Scene();
                scene.setProject(chapter.getProject());
                scene.setChapters(new ArrayList<>(List.of(chapter)));
                scene.setSummary(detected.summary());
                scene.setType(parseSceneType(detected.type()));
                scene.setLocation(detected.location());
                scene.setTensionLevel(detected.tensionLevel());
                scene.setPace(parsePace(detected.pace()));
                scene.setTone(parseTone(detected.tone()));
                scene.setNarrativeTimeType(parseNarrativeTimeType(detected.narrativeTimeType()));
                scene.setFlashbackToChapter(detected.flashbackToChapter());
                scene.setCreatedAt(LocalDateTime.now());

                if (detected.narrativeTimeType() != null
                        && !"PRESENT".equals(detected.narrativeTimeType())) {
                    logger.info("Chapter {}: detected {} scene (flashback to chapter {})",
                            chapter.getChapterNumber(),
                            detected.narrativeTimeType(),
                            detected.flashbackToChapter());
                }

                Scene saved = sceneRepository.save(scene);
                scenes.add(saved);
                embeddingService.embedScene(saved);
            }

            return scenes;

        } catch (Exception e) {
            throw new AiServiceException("primary", "scene-analysis",
                    "Failed to analyze scenes for chapter " + chapter.getId(), e);
        }
    }

    private Scene findExistingScene(Chapter chapter, SceneAnalysisResult.DetectedScene detected) {
        List<Scene> projectScenes = sceneRepository.findByProjectId(chapter.getProject().getId());
        for (Scene existing : projectScenes) {
            if (existing.getType() == parseSceneType(detected.type()) &&
                existing.getLocation() != null &&
                existing.getLocation().equals(detected.location())) {
                return existing;
            }
        }
        return null;
    }

    private SceneType parseSceneType(String type) {
        try { return SceneType.valueOf(type); }
        catch (Exception e) { return SceneType.DIALOGUE; }
    }

    private NarrativePace parsePace(String pace) {
        try { return NarrativePace.valueOf(pace); }
        catch (Exception e) { return NarrativePace.MODERATE; }
    }

    private EmotionalTone parseTone(String tone) {
        try { return EmotionalTone.valueOf(tone); }
        catch (Exception e) { return EmotionalTone.SERIOUS; }
    }

    private NarrativeTimeType parseNarrativeTimeType(String type) {
        try { return NarrativeTimeType.valueOf(type); }
        catch (Exception e) { return NarrativeTimeType.PRESENT; }
    }

    private static final String SYSTEM_PROMPT = """
            You are a literary analyst specializing in narrative structure.
            Given a chapter from a novel, identify distinct scenes.

            A scene changes when there is a significant shift in:
            - Location (characters move to a new place)
            - Time (time skip or temporal shift)
            - Participants (major character enters/exits)
            - Narrative focus (shift from action to introspection, etc.)

            For each scene, determine:
            - summary: 1-2 sentence summary of what happens
            - type: One of DIALOGUE, ACTION, BATTLE, INTROSPECTION, ROMANCE, EXPOSITION, TRANSITION
            - location: Where the scene takes place (use "Unknown" if unclear)
            - tensionLevel: 0.0 (calm daily life) to 1.0 (climactic confrontation)
            - pace: One of SLOW, MODERATE, FAST, FRANTIC
            - tone: One of SERIOUS, HUMOROUS, MELANCHOLIC, TRIUMPHANT, MYSTERIOUS, TENSE
            - continuedFromPrevious: true if this scene clearly started in a prior chapter
            - continuesInNext: true if this scene is clearly unfinished at the chapter's end
            - narrativeTimeType: The temporal classification of this scene.
                  Use PRESENT for the main story timeline (the vast majority of scenes).
                  Use FLASHBACK when the narrative explicitly moves to events that occurred
                  BEFORE the story's current timeline (memory sequences, "years ago" scenes,
                  characters reliving the past). Use FLASH_FORWARD for visions or scenes
                  set explicitly in the future.
            - flashbackToChapter: For FLASHBACK scenes only — the approximate chapter number
                  the scene is set in (e.g. if the flashback is to events before chapter 1,
                  use 0; if it's to roughly chapter 5 events, use 5).
                  Leave null for PRESENT and FLASH_FORWARD scenes.

            Respond ONLY with valid JSON matching the requested format.
            """;
}