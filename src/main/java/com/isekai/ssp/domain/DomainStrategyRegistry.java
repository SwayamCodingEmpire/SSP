package com.isekai.ssp.domain;

import com.isekai.ssp.helpers.ContentFamily;
import com.isekai.ssp.helpers.ContentType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resolves the appropriate DomainStrategy for a given ContentType.
 * Strategies are registered per ContentFamily. Falls back to NARRATIVE_FICTION
 * if no strategy is found for the requested family.
 */
@Component
public class DomainStrategyRegistry {

    private final Map<ContentFamily, DomainStrategy> strategies;

    public DomainStrategyRegistry(List<DomainStrategy> allStrategies) {
        this.strategies = allStrategies.stream()
                .collect(Collectors.toMap(DomainStrategy::getFamily, s -> s));
    }

    public DomainStrategy resolve(ContentType type) {
        if (type == null) {
            return strategies.get(ContentFamily.NARRATIVE_FICTION);
        }
        return strategies.getOrDefault(type.getFamily(),
                strategies.get(ContentFamily.NARRATIVE_FICTION));
    }
}
