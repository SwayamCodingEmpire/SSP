package com.isekai.ssp.controller;

import com.isekai.ssp.domain.DomainStrategy;
import com.isekai.ssp.domain.DomainStrategyRegistry;
import com.isekai.ssp.domain.QualityDimension;
import com.isekai.ssp.helpers.ContentType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/content-types")
public class ContentTypeController {

    private final DomainStrategyRegistry strategyRegistry;

    public ContentTypeController(DomainStrategyRegistry strategyRegistry) {
        this.strategyRegistry = strategyRegistry;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> listContentTypes() {
        List<Map<String, Object>> types = Arrays.stream(ContentType.values())
                .map(ct -> Map.<String, Object>of(
                        "name", ct.name(),
                        "family", ct.getFamily().name(),
                        "contentElementTypes", strategyRegistry.resolve(ct).getContentElementTypes(),
                        "glossaryCategories", strategyRegistry.resolve(ct).getGlossaryCategories()
                ))
                .toList();
        return ResponseEntity.ok(types);
    }

    @GetMapping(value = "/{type}/quality-dimensions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> getQualityDimensions(@PathVariable String type) {
        ContentType contentType;
        try {
            contentType = ContentType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        DomainStrategy strategy = strategyRegistry.resolve(contentType);
        List<String> dimensions = strategy.getQualityDimensions().stream()
                .map(QualityDimension::name)
                .toList();
        return ResponseEntity.ok(dimensions);
    }
}
