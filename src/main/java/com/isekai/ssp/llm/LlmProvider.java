package com.isekai.ssp.llm;

/**
 * Abstraction over an AI language model provider.
 * Implementations: {@link OpenAiProvider}, {@link AnthropicProvider}.
 * Use {@link LlmProviderRegistry} to resolve a provider by name at runtime.
 */
public interface LlmProvider {

    /** Provider identifier — matches values accepted by the API (e.g. "openai", "anthropic"). */
    String getName();

    /** Send a prompt and return the raw text response. */
    String generate(String systemPrompt, String userPrompt);
}
