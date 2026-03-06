package com.isekai.ssp.llm;

import com.isekai.ssp.helpers.ContentFamily;
import com.isekai.ssp.helpers.ContentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Suggests the optimal LLM provider based on content type and translation pass.
 * Can be overridden by explicit provider selection in the API.
 */
@Component
public class ModelRoutingStrategy {

    @Value("${ssp.ai.vllm.enabled:false}")
    private boolean vllmEnabled;

    @Value("${ssp.ai.active-provider:openai}")
    private String defaultProvider;

    /**
     * Suggests the best provider for a given content type and translation pass.
     *
     * @param type content type being translated
     * @param pass 1 for faithful draft, 2 for elevation/refinement
     * @return suggested provider name
     */
    public String suggestProvider(ContentType type, int pass) {
        if (type == null) return defaultProvider;

        ContentFamily family = type.getFamily();

        // Poetry and song lyrics always use the most capable cloud model
        if (family == ContentFamily.POETIC) {
            return defaultProvider;
        }

        // Pass 1 (faithful draft) can use local model if available
        if (pass == 1 && vllmEnabled) {
            return "vllm";
        }

        // Pass 2 (elevation) uses cloud model for quality
        return defaultProvider;
    }
}
