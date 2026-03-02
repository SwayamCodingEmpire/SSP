package com.isekai.ssp.service;

import com.isekai.ssp.dto.SceneAnalysisResult;
import com.isekai.ssp.entities.Chapter;
import com.isekai.ssp.entities.Scene;
import com.isekai.ssp.helpers.EmotionalTone;
import com.isekai.ssp.helpers.NarrativePace;
import com.isekai.ssp.helpers.SceneType;
import com.isekai.ssp.repository.SceneRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SceneAnalysisService {

    private final ChatClient chatClient;
    private final SceneRepository sceneRepository;
    private final ContextBuilderService contextBuilder;

    public SceneAnalysisService(
            ChatClient chatClient,
            SceneRepository sceneRepository,
            ContextBuilderService contextBuilder) {
        this.chatClient = chatClient;
        this.sceneRepository = sceneRepository;
        this.contextBuilder = contextBuilder;
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
                    // Try to find an existing scene in this project with similar summary
                    scene = findExistingScene(chapter, detected);
                    if (scene != null) {
                        // Link this chapter to the existing scene
                        scene.getChapters().add(chapter);
                        sceneRepository.save(scene);
                        scenes.add(scene);
                        continue;
                    }
                }

                // Create new scene
                scene = new Scene();
                scene.setProject(chapter.getProject());
                scene.setChapters(new ArrayList<>(List.of(chapter)));
                scene.setSummary(detected.summary());
                scene.setType(parseSceneType(detected.type()));
                scene.setLocation(detected.location());
                scene.setTensionLevel(detected.tensionLevel());
                scene.setPace(parsePace(detected.pace()));
                scene.setTone(parseTone(detected.tone()));
                scene.setCreatedAt(LocalDateTime.now());
                scenes.add(sceneRepository.save(scene));
            }

            return scenes;

        } catch (Exception e) {
            throw new AiServiceException("primary", "scene-analysis",
                    "Failed to analyze scenes for chapter " + chapter.getId(), e);
        }
    }

    /**
     * Attempts to find an existing scene in the project that matches a continued scene.
     * Uses summary text matching as a simple heuristic.
     */
    private Scene findExistingScene(Chapter chapter, SceneAnalysisResult.DetectedScene detected) {
        List<Scene> projectScenes = sceneRepository.findByProjectId(chapter.getProject().getId());
        for (Scene existing : projectScenes) {
            if (existing.getSummary() != null &&
                detected.summary() != null &&
                existing.getType() == parseSceneType(detected.type()) &&
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

    private static final String SYSTEM_PROMPT = """
            You are a literary analyst specializing in narrative structure.
            Given a chapter from a novel, identify distinct scenes.

            A scene changes when there is a significant shift in:
            - Location (characters move to a new place)
            - Time (time skip)
            - Participants (major character enters/exits)
            - Narrative focus (shift from action to introspection)

            For each scene, determine:
            - summary: 1-2 sentence summary of what happens
            - type: One of DIALOGUE, ACTION, BATTLE, INTROSPECTION, ROMANCE, EXPOSITION, TRANSITION
            - location: Where the scene takes place (use "Unknown" if unclear)
            - tensionLevel: 0.0 (calm daily life) to 1.0 (climactic confrontation)
            - pace: One of SLOW, MODERATE, FAST, FRANTIC
            - tone: One of SERIOUS, HUMOROUS, MELANCHOLIC, TRIUMPHANT, MYSTERIOUS, TENSE
            - continuedFromPrevious: true if this scene clearly started in a prior chapter
            - continuesInNext: true if this scene is clearly unfinished at the chapter's end

            Respond ONLY with valid JSON matching the requested format.
            """;
}
