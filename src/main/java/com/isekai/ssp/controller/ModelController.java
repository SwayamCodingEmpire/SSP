package com.isekai.ssp.controller;

import com.isekai.ssp.entities.ModelAdapter;
import com.isekai.ssp.llm.LlmProvider;
import com.isekai.ssp.llm.LlmProviderRegistry;
import com.isekai.ssp.repository.ModelAdapterRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final LlmProviderRegistry providerRegistry;
    private final ModelAdapterRepository adapterRepository;

    public ModelController(
            LlmProviderRegistry providerRegistry,
            ModelAdapterRepository adapterRepository) {
        this.providerRegistry = providerRegistry;
        this.adapterRepository = adapterRepository;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getAvailableModels() {
        String defaultProvider = providerRegistry.getDefaultName();

        List<Map<String, Object>> adapters = adapterRepository.findByActiveTrue().stream()
                .map(a -> Map.<String, Object>of(
                        "id", a.getId(),
                        "name", a.getName(),
                        "baseModel", a.getBaseModel(),
                        "targetFamily", a.getTargetFamily() != null ? a.getTargetFamily().name() : "ANY",
                        "languagePair", a.getLanguagePair() != null ? a.getLanguagePair() : "any",
                        "qualityScore", a.getQualityScore() != null ? a.getQualityScore() : 0.0f
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "defaultProvider", defaultProvider,
                "adapters", adapters
        ));
    }
}
