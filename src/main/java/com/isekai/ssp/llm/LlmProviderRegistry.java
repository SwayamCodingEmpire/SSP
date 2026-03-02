package com.isekai.ssp.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the active {@link LlmProvider} by name.
 * All {@code LlmProvider} beans are auto-collected via Spring's list injection.
 * Falls back to the configured default provider when the requested name is unknown or null.
 */
@Component
public class LlmProviderRegistry {

    private final Map<String, LlmProvider> providers;
    private final String defaultProviderName;

    public LlmProviderRegistry(
            List<LlmProvider> providers,
            @Value("${ssp.ai.active-provider:openai}") String defaultProviderName) {
        this.providers = providers.stream()
                .collect(Collectors.toMap(LlmProvider::getName, Function.identity()));
        this.defaultProviderName = defaultProviderName.toLowerCase();
    }

    /**
     * Resolve a provider by name. Falls back to the default if name is null or unknown.
     *
     * @param name  provider name from API request (e.g. "openai", "anthropic"), or null
     * @return the matching {@link LlmProvider}
     */
    public LlmProvider resolve(String name) {
        if (name != null && providers.containsKey(name.toLowerCase())) {
            return providers.get(name.toLowerCase());
        }
        return getDefault();
    }

    public LlmProvider getDefault() {
        LlmProvider provider = providers.get(defaultProviderName);
        if (provider == null) {
            throw new IllegalStateException(
                    "No LlmProvider registered for default provider: " + defaultProviderName +
                    ". Available: " + providers.keySet());
        }
        return provider;
    }

    public String getDefaultName() {
        return defaultProviderName;
    }
}